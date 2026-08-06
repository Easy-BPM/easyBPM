package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.CallActivityMapping
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.variable.VariableMappingService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Service for handling call activity (subprocess) execution and variable mapping.
 * 
 * Responsibilities:
 * 1. Create child process instances from call activity nodes
 * 2. Apply input variable mapping (parent → child)
 * 3. Manage parent-child relationship lifecycle
 * 4. Apply output variable mapping (child → parent)
 * 5. Handle error propagation from child to parent
 */
@Service
class CallActivityHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val callActivityMappingRepository: CallActivityMappingRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val variableMappingService: VariableMappingService,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CallActivityHandler::class.java)
        const val DEFAULT_MAX_NESTING_LEVEL = 10
    }

    /**
     * Execute a call activity node by creating and starting a child process instance.
     * 
     * @param parentInstance The parent process instance executing the call activity
     * @param callActivityNode The call activity node configuration
     * @param definition The parent process definition
     * @return true if successfully created child instance, false otherwise
     * @throws IllegalArgumentException if nesting depth exceeds limit or subprocess not found
     */
    @Transactional
    fun executeCallActivity(
        parentInstance: ProcessInstance,
        callActivityNode: JsonNode,
        definition: JsonNode
    ): Boolean {
        try {
            val callActivityNodeId = callActivityNode.get("id").asText()
            val targetProcessKey = callActivityNode.get("config")?.get("processKey")?.asText()
                ?: throw IllegalArgumentException("Call activity node '$callActivityNodeId' missing 'processKey' in config")

            // Step 1: Validate nesting depth
            val currentNestingLevel = parentInstance.nestingLevel
            if (currentNestingLevel >= DEFAULT_MAX_NESTING_LEVEL) {
                throw IllegalArgumentException(
                    "Cannot execute call activity: nesting depth (${currentNestingLevel + 1}) exceeds maximum ($DEFAULT_MAX_NESTING_LEVEL)"
                )
            }

            // Step 2: Find target process definition
            val childDefinition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(targetProcessKey)
                ?: throw IllegalArgumentException("Process definition not found for key: '$targetProcessKey'")

            // Step 3: Detect circular references
            detectCircularReference(parentInstance, childDefinition)

            // Step 4: Create child process instance with parent context
            val childInstance = createChildInstance(
                parentInstance = parentInstance,
                childDefinition = childDefinition,
                callActivityNodeId = callActivityNodeId,
                nestingLevel = currentNestingLevel + 1
            )

            logger.info(
                "Created child process instance {} for call activity '{}' (parent={}, level={})",
                childInstance.id, callActivityNodeId, parentInstance.id, childInstance.nestingLevel
            )

            // Step 5: Register mapping with input/output configurations
            val mapping = registerCallActivityMapping(
                parentInstance = parentInstance,
                childInstance = childInstance,
                callActivityNode = callActivityNode
            )

            logger.debug(
                "Registered call activity mapping: parent={}, child={}, node={}",
                parentInstance.id, childInstance.id, callActivityNodeId
            )

            // Step 6: Apply input variable mapping
            applyInputVariableMapping(
                parentInstance = parentInstance,
                childInstance = childInstance,
                mapping = mapping
            )

            // Step 7: Suspend parent process while child executes
            // Save current node as completion node (where to resume after child finishes)
            parentInstance.completionNodeId = callActivityNodeId
            parentInstance.currentNode = emptyList<String>().toMutableList()
            parentInstance.status = ProcessStatus.WAITING
            processInstanceRepository.save(parentInstance)

            logger.debug(
                "Parent instance {} suspended at call activity '{}' (child will resume at completion)",
                parentInstance.id, callActivityNodeId
            )

            // Step 8: Child process starts independently
            // Note: The child will execute in its own transaction context
            // Parent will remain suspended until child completes or errors occur

            return true

        } catch (ex: Exception) {
            logger.error("Error executing call activity node", ex)
            throw ex
        }
    }

    /**
     * Handle completion of a child process instance and resume parent.
     * 
     * Manages state transitions:
     * - Child status → COMPLETED
     * - Parent status → ACTIVE (resume)
     * - Parent currentNode → Next nodes after call activity
     * - Cleanup call activity mapping
     * 
     * @param childInstance The completed child process instance
     */
    @Transactional
    fun handleChildCompletion(childInstance: ProcessInstance) {
        try {
            // Step 1: Find the parent-child mapping
            val mapping = callActivityMappingRepository.findByChildInstanceId(childInstance.id)
                ?: run {
                    logger.warn("No mapping found for child instance {}", childInstance.id)
                    return
                }

            val parentInstance = processInstanceRepository.findById(mapping.parentInstanceId)
                .orElseThrow { IllegalArgumentException("Parent instance not found: ${mapping.parentInstanceId}") }

            logger.info(
                "Handling completion of child process instance {} (parent={})",
                childInstance.id, parentInstance.id
            )

            // Step 2: Update child instance status to COMPLETED
            childInstance.status = ProcessStatus.COMPLETED
            processInstanceRepository.save(childInstance)

            // Step 3: Apply output variable mapping (child → parent)
            applyOutputVariableMapping(
                parentInstance = parentInstance,
                childInstance = childInstance,
                mapping = mapping
            )

            logger.debug("Output variable mapping applied for child {}", childInstance.id)

            // Step 4: Resume parent process from call activity node
            // Get the parent's definition to find next nodes after call activity
            val definition = objectMapper.readTree(parentInstance.processDefinition.definitionJson)
            val callActivityNode = definition.get("nodes").firstOrNull {
                it.get("id").asText() == mapping.callActivityNodeId
            } ?: throw IllegalStateException("Call activity node '${mapping.callActivityNodeId}' not found in definition")

            // Find next nodes after call activity node
            val nextNodes = getNextNodes(callActivityNode, definition)

            // Step 5: Update parent state - resume to next nodes
            parentInstance.currentNode = nextNodes.map { it.get("id").asText() }.toMutableList()
            parentInstance.status = ProcessStatus.ACTIVE
            parentInstance.completionNodeId = null
            processInstanceRepository.save(parentInstance)

            logger.debug(
                "Parent {} resumed to ACTIVE state at nodes: {}",
                parentInstance.id, parentInstance.currentNode
            )

            // Step 6: Clean up mapping record
            callActivityMappingRepository.delete(mapping)

            logger.debug(
                "Call activity mapping cleaned up: parent={}, child={}",
                parentInstance.id, childInstance.id
            )

        } catch (ex: Exception) {
            logger.error("Error handling child process completion", ex)
            throw ex
        }
    }

    /**
     * Propagate an error from child process to parent's error boundary.
     * 
     * If an error boundary is attached to the call activity node:
     * - Capture error message to exceptionVariable
     * - Route parent to error boundary's next nodes
     * - Resume parent execution
     * 
     * If no error boundary:
     * - Mark parent as FAILED
     * - Parent execution stops
     * 
     * @param childInstance The failed child process instance
     * @param errorMessage The error message from child
     * @param parentDefinitionJson The parent process definition (as JSON string)
     */
    @Transactional
    fun propagateErrorToParent(childInstance: ProcessInstance, errorMessage: String, parentDefinitionJson: String) {
        try {
            val mapping = callActivityMappingRepository.findByChildInstanceId(childInstance.id)
                ?: run {
                    logger.warn("No mapping found for failed child instance {}", childInstance.id)
                    return
                }

            val parentInstance = processInstanceRepository.findById(mapping.parentInstanceId)
                .orElseThrow { IllegalArgumentException("Parent instance not found: ${mapping.parentInstanceId}") }

            logger.warn(
                "Propagating error from child {} to parent {}: {}",
                childInstance.id, parentInstance.id, errorMessage
            )

            // Step 1: Update child instance status to FAILED
            childInstance.status = ProcessStatus.FAILED
            processInstanceRepository.save(childInstance)

            // Step 2: Parse parent definition and find call activity node
            val definition = objectMapper.readTree(parentDefinitionJson)
            val callActivityNode = definition.get("nodes").firstOrNull {
                it.get("id").asText() == mapping.callActivityNodeId
            } ?: throw IllegalStateException("Call activity node '${mapping.callActivityNodeId}' not found in parent definition")

            // Step 3: Look for error boundary attached to call activity
            val errorBoundaryNode = findAttachedErrorBoundary(callActivityNode, definition)

            if (errorBoundaryNode != null) {
                // Error boundary exists - handle the error
                logger.info(
                    "Error boundary found for call activity '{}' on parent {}",
                    mapping.callActivityNodeId, parentInstance.id
                )

                val errorCode = errorBoundaryNode.get("config")?.get("errorCode")?.asText() ?: "ERROR"
                val exceptionVariableName = errorBoundaryNode.get("config")?.get("exceptionVariable")?.asText()

                // Capture error message to process variable if configured
                if (!exceptionVariableName.isNullOrBlank()) {
                    captureErrorVariable(parentInstance.id, exceptionVariableName, errorMessage)
                    logger.debug("Error message captured to variable '{}' in parent {}", exceptionVariableName, parentInstance.id)
                }

                // Route parent to error boundary's next nodes
                val nextNodes = getNextNodes(errorBoundaryNode, definition)
                parentInstance.currentNode = nextNodes.map { it.get("id").asText() }.toMutableList()
                parentInstance.status = ProcessStatus.ACTIVE
                parentInstance.completionNodeId = null
                processInstanceRepository.save(parentInstance)

                logger.info(
                    "Parent {} resumed to ACTIVE state at error boundary next nodes: {}",
                    parentInstance.id, parentInstance.currentNode
                )
            } else {
                // No error boundary - propagate as parent failure
                logger.warn(
                    "No error boundary for call activity '{}' - marking parent {} as FAILED",
                    mapping.callActivityNodeId, parentInstance.id
                )

                parentInstance.status = ProcessStatus.FAILED
                parentInstance.currentNode = emptyList<String>().toMutableList()
                parentInstance.completionNodeId = null
                parentInstance.updatedAt = LocalDateTime.now()
                processInstanceRepository.save(parentInstance)
            }

            // Step 4: Clean up mapping record
            callActivityMappingRepository.delete(mapping)

            logger.debug(
                "Call activity mapping cleaned up after error propagation: parent={}, child={}",
                parentInstance.id, childInstance.id
            )

        } catch (ex: Exception) {
            logger.error("Error propagating error from child to parent", ex)
            throw ex
        }
    }

    /**
     * Reapply input variable mappings for all children of the given parent instance.
     * This is useful when variables are assigned to the parent after the child was created.
     * 
     * @param parentInstance The parent process instance
     */
    @Transactional
    fun reapplyInputMappingsForChildren(parentInstance: ProcessInstance) {
        try {
            val mappings = callActivityMappingRepository.findByParentInstanceId(parentInstance.id)
            logger.debug("Reapplying input mappings for {} children of parent {}", mappings.size, parentInstance.id)
            
            for (mapping in mappings) {
                val childInstance = processInstanceRepository.findById(mapping.childInstanceId)
                    .orElse(null) ?: continue
                    
                applyInputVariableMapping(parentInstance, childInstance, mapping)
                logger.debug("Reapplied input mappings for child {} of parent {}", childInstance.id, parentInstance.id)
            }
        } catch (ex: Exception) {
            logger.error("Error reapplying input mappings", ex)
            throw ex
        }
    }

    /**
     * Find an ErrorBoundaryEvent node attached to the given node.
     */
    private fun findAttachedErrorBoundary(node: JsonNode, definition: JsonNode): JsonNode? {
        val nodeId = node.get("id").asText()
        val nodes = definition.get("nodes")
        return nodes.firstOrNull {
            try {
                NodeType.fromString(it.get("type").asText()) == NodeType.ErrorBoundaryEvent &&
                it.has("attachedTo") && it.get("attachedTo").asText() == nodeId
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Capture error message to a process variable.
     */
    @Transactional
    private fun captureErrorVariable(processInstanceId: Long, variableName: String, errorMessage: String) {
        try {
            val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, variableName)
            
            val variable = if (existing != null) {
                existing.copy(
                    value = objectMapper.valueToTree(errorMessage),
                    updatedAt = LocalDateTime.now()
                )
            } else {
                ProcessVariable(
                    processInstanceId = processInstanceId,
                    name = variableName,
                    value = objectMapper.valueToTree(errorMessage),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            }
            
            processVariableRepository.save(variable)
        } catch (ex: Exception) {
            logger.warn("Failed to capture error message to variable '{}': {}", variableName, ex.message)
        }
    }

    /**
     * Create a child process instance with parent context.
     */
    @Transactional
    private fun createChildInstance(
        parentInstance: ProcessInstance,
        childDefinition: ProcessDefinition,
        callActivityNodeId: String,
        nestingLevel: Int
    ): ProcessInstance {
        val childInstance = ProcessInstance(
            processDefinition = childDefinition,
            status = ProcessStatus.ACTIVE,
            currentNode = mutableListOf(),
            parentInstanceId = parentInstance.id,
            callActivityNodeId = callActivityNodeId,
            nestingLevel = nestingLevel,
            completionNodeId = null
        )

        return processInstanceRepository.save(childInstance)
    }

    /**
     * Register call activity mapping with variable configuration.
     */
    @Transactional
    private fun registerCallActivityMapping(
        parentInstance: ProcessInstance,
        childInstance: ProcessInstance,
        callActivityNode: JsonNode
    ): CallActivityMapping {
        val config = callActivityNode.get("config") ?: return createDefaultMapping(parentInstance, childInstance, callActivityNode)

        val inputMappingsJson = config.get("inputMappings")?.toString() ?: "{}"
        val outputMappingsJson = config.get("outputMappings")?.toString() ?: "{}"
        val propagateAll = config.get("propagateAllVariables")?.asBoolean() ?: false

        val mapping = CallActivityMapping(
            parentInstanceId = parentInstance.id,
            childInstanceId = childInstance.id,
            callActivityNodeId = callActivityNode.get("id").asText(),
            inputMappings = inputMappingsJson,
            outputMappings = outputMappingsJson,
            propagateAllVariables = propagateAll
        )

        return callActivityMappingRepository.save(mapping)
    }

    private fun createDefaultMapping(
        parentInstance: ProcessInstance,
        childInstance: ProcessInstance,
        callActivityNode: JsonNode
    ): CallActivityMapping {
        val mapping = CallActivityMapping(
            parentInstanceId = parentInstance.id,
            childInstanceId = childInstance.id,
            callActivityNodeId = callActivityNode.get("id").asText(),
            inputMappings = "{}",
            outputMappings = "{}",
            propagateAllVariables = false
        )
        return callActivityMappingRepository.save(mapping)
    }

    /**
     * Apply input variable mapping (parent → child).
     */
    @Transactional
    private fun applyInputVariableMapping(
        parentInstance: ProcessInstance,
        childInstance: ProcessInstance,
        mapping: CallActivityMapping
    ) {
        val inputMappings = mapping.getInputMappingsAsMap()
        val stats = variableMappingService.applyInputMappings(
            sourceInstanceId = parentInstance.id,
            targetInstanceId = childInstance.id,
            mappings = inputMappings,
            propagateAll = mapping.propagateAllVariables
        )
        logger.debug("Input variable mapping completed for call activity: {}", stats)
    }

    /**
     * Apply output variable mapping (child → parent).
     */
    @Transactional
    private fun applyOutputVariableMapping(
        parentInstance: ProcessInstance,
        childInstance: ProcessInstance,
        mapping: CallActivityMapping
    ) {
        val outputMappings = mapping.getOutputMappingsAsMap()
        val stats = variableMappingService.applyOutputMappings(
            sourceInstanceId = childInstance.id,
            targetInstanceId = parentInstance.id,
            mappings = outputMappings,
            propagateAll = mapping.propagateAllVariables
        )
        logger.debug("Output variable mapping completed for call activity: {}", stats)
    }

    /**
     * Start execution of the child process by finding and executing its start nodes.
     */
    /**
     * Detect circular references (A → B → A) to prevent infinite loops.
     */
    private fun detectCircularReference(
        parentInstance: ProcessInstance,
        targetDefinition: ProcessDefinition
    ) {
        // Walk up the parent chain to detect cycles
        var currentParentId = parentInstance.parentInstanceId
        while (currentParentId != null) {
            val ancestor = processInstanceRepository.findById(currentParentId)
                .orElse(null) ?: break

            if (ancestor.processDefinition.id == targetDefinition.id) {
                throw IllegalArgumentException(
                    "Circular reference detected: process '${targetDefinition.key}' already in parent chain"
                )
            }
            currentParentId = ancestor.parentInstanceId
        }
    }

    /**
     * Get next nodes in the process definition (copied from ProcessService).
     * TODO: Extract to shared utility
     */
    private fun getNextNodes(node: JsonNode, definition: JsonNode): List<JsonNode> {
        // Try both "edges" (from modeler) and "flows" (from direct JSON)
        var edges = definition.get("edges")
        if (edges == null || edges.isMissingNode) {
            edges = definition.get("flows")
        }
        if (edges == null || edges.isMissingNode) {
            return emptyList()
        }
        
        val nodeId = node.get("id").asText()

        return edges.filter { edge ->
            edge.get("source").asText() == nodeId
        }.mapNotNull { edge ->
            val targetId = edge.get("target").asText()
            definition.get("nodes").firstOrNull {
                it.get("id").asText() == targetId
            }
        }
    }
}
