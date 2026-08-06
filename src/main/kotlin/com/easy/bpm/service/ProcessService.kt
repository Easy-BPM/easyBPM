package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Service
class ProcessService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService,
    private val callActivityMappingRepository: CallActivityMappingRepository,
    private val timelineService: ProcessInstanceTimelineService,
    private val pageableSanitizer: ProcessPageableSanitizer,
    private val processDefinitionValidator: ProcessDefinitionValidator,
    private val variableManager: ProcessVariableManager,
    private val navigator: ProcessNavigator,
    private val workerCallbackHandler: ProcessWorkerCallbackHandler,
    private val messageReceivedHandler: ProcessMessageReceivedHandler,
    private val messageStartResolver: ProcessMessageStartResolver,
    private val lifecycleManager: ProcessInstanceLifecycleManager,
    private val executionEngine: ProcessExecutionEngine
) {

    companion object {
        const val INTERNAL_TIMER_MESSAGE_NAME = "__internal.timer__"
        private val logger = LoggerFactory.getLogger(ProcessService::class.java)
    }

    /* =========================
       DEPLOY
     ========================= */

    @Transactional
    fun deployProcess(definitionJson: JsonNode): ProcessDefinition {
        val json = processDefinitionValidator.validateAndParse(definitionJson)

        val processId = json.get("processId").asText()
        val processKey = json.get("key")?.asText()?.takeIf { it.isNotBlank() } ?: processId
        val processName = json.get("processName")?.asText()?.takeIf { it.isNotBlank() }
            ?: json.get("name")?.asText()?.takeIf { it.isNotBlank() } ?: processId
        val processDescription =
            json.get("description")?.asText()?.takeIf { it.isNotBlank() }
                ?: json.get("metadata")?.get("description")?.asText()?.takeIf { it.isNotBlank() }

        val latestVersion =
            processDefinitionRepository.findTopByKeyOrderByVersionDesc(processKey)

        val nextVersion = (latestVersion?.version ?: 0) + 1

        return processDefinitionRepository.save(
                ProcessDefinition(
                key = processKey,
                    processName = processName,
                description = processDescription,
                definitionJson = json.toString(),
                version = nextVersion
            )
        )
    }

    /* =========================
       START PROCESS
     ========================= */

    @Transactional
    fun startProcessInstance(processDefinitionId: Long): ProcessInstance {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        return startWithDefinition(definition)
    }

    fun startProcessInstance(processDefinitionId: Long, initialVariables: Map<String, Any>): ProcessInstance {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        return startWithDefinition(definition, initialVariables)
    }

    fun startProcessInstance(processId: String): ProcessInstance {
        val definition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
            ?: throw IllegalArgumentException("Process definition not found for id: $processId")

        return startWithDefinition(definition)
    }

    fun startProcessInstance(processId: String, initialVariables: Map<String, Any>): ProcessInstance {
        val definition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
            ?: throw IllegalArgumentException("Process definition not found for id: $processId")

        return startWithDefinition(definition, initialVariables)
    }

    @Transactional
    private fun startWithDefinition(
        definition: ProcessDefinition,
        initialVariables: Map<String, Any> = emptyMap(),
        startNodeId: String? = null
    ): ProcessInstance {
        val startTime = System.currentTimeMillis()

        val json = parseDefinition(definition.definitionJson)

        val instance = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )
        )

        metricsService.recordProcessStarted()
        timelineService.record(
            processInstanceId = instance.id,
            eventType = ProcessInstanceEventType.PROCESS_STARTED,
            message = "Process instance started."
        )

        variableManager.initializeProcessVariables(instance, json)
        
        // Add initial variables if provided
        if (initialVariables.isNotEmpty()) {
            initialVariables.forEach { (name, value) ->
                variableManager.upsertProcessVariable(instance.id, name, objectMapper.valueToTree(value))
            }
        }

        val startNodes = navigator.getStartNodes(instance, json, startNodeId)

        instance.currentNode = startNodes
        instance.nodeHistory = startNodes
        processInstanceRepository.save(instance)
        startNodes.forEach { nodeId ->
            timelineService.record(
                processInstanceId = instance.id,
                nodeId = nodeId,
                eventType = ProcessInstanceEventType.NODE_ENTERED,
                message = "Entered node '$nodeId'."
            )
        }

        executionEngine.executeNodes(startNodes, instance, json)

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordProcessExecution(duration)

        return instance
    }

    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> =
        processInstanceRepository.findAll(pageableSanitizer.sanitizeProcessInstances(pageable))

    fun getProcessInstanceById(id: Long): ProcessInstance? =
        processInstanceRepository.findById(id).orElse(null)

    fun getChildProcessInstances(parentInstanceId: Long): List<ProcessInstance> {
        processInstanceRepository.findById(parentInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        return processInstanceRepository.findByParentInstanceId(parentInstanceId)
    }

    fun getParentProcessInstance(childInstanceId: Long): ProcessInstance? {
        val child = processInstanceRepository.findById(childInstanceId).orElse(null) ?: return null
        val parentId = child.parentInstanceId ?: return null
        return processInstanceRepository.findById(parentId).orElse(null)
    }

    fun getCallActivityMapping(parentInstanceId: Long, childInstanceId: Long) =
        callActivityMappingRepository.findByParentInstanceIdAndChildInstanceId(parentInstanceId, childInstanceId)

    fun getProcessVariables(processInstanceId: Long): List<ProcessVariable> {
        processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        return processVariableRepository.findByProcessInstanceId(processInstanceId)
    }

    @Transactional
    fun assignProcessVariables(processInstanceId: Long, variables: Map<String, Any?>): List<ProcessVariable> =
        variableManager.assignProcessVariables(processInstanceId, variables)

    @Transactional
    fun moveProcessNode(processInstanceId: Long, fromNode: String, toNode: String): ProcessInstance =
        lifecycleManager.moveProcessNode(processInstanceId, fromNode, toNode)

    fun getLatestProcessDefinitions(pageable: Pageable): Page<ProcessDefinition> =
        processDefinitionRepository.findLatestVersionProcesses(pageableSanitizer.sanitizeProcessDefinitions(pageable))

    fun getProcessDefinitionById(id: Long): ProcessDefinition? =
        processDefinitionRepository.findById(id).orElse(null)

    @Transactional
    fun stopProcessInstance(id: Long): ProcessInstance =
        lifecycleManager.stopProcessInstance(id)

    @Transactional
    fun deleteProcessInstance(id: Long) =
        lifecycleManager.deleteProcessInstance(id)

    @Transactional
    fun handleServiceTaskCompleted(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val result = workerCallbackHandler.handleCompleted(processInstanceId, nodeId, outputs)
        continueFromWorkerCallback(result)
    }

    @Transactional
    fun handleServiceTaskFailed(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String? = null,
        incidentSource: IncidentSource = IncidentSource.WORKER,
        externalReferenceId: String? = null
        ,
        createIncident: Boolean = true
    ) {
        val result = workerCallbackHandler.handleFailed(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage ?: "Service task failed",
            incidentSource = incidentSource,
            externalReferenceId = externalReferenceId
        )
        continueFromWorkerCallback(result)
    }

    private fun continueFromWorkerCallback(result: WorkerCallbackResult) {
        if (result.shouldContinue) {
            navigator.advanceProcess(result.instance, result.nextNodes, result.definition)
            executionEngine.executeNodes(result.nextNodes, result.instance, result.definition)
        }

        val duration = System.currentTimeMillis() - result.startTime
        metricsService.recordServiceTaskExecution(duration, success = result.success)
    }

    fun markServiceTaskTimedOut(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String,
        externalReferenceId: String? = null
    ) {
        handleServiceTaskFailed(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage,
            incidentSource = IncidentSource.WORKER,
            externalReferenceId = externalReferenceId
        )
    }

    @Transactional
    fun handleMessageReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ) {
        val result = messageReceivedHandler.handleReceived(messageName, correlationKey, variables)
        if (!result.subscriptionFound) {
            val startedInstance = startProcessInstanceFromMessageStart(messageName, correlationKey, variables)
                ?: throw IllegalArgumentException("No waiting subscription or message start event for message '$messageName' with correlationKey '$correlationKey'")
            logger.info("Started process instance ${startedInstance.id} from message '$messageName'")
            return
        }

        val instance = requireNotNull(result.instance)
        val definition = requireNotNull(result.definition)
        navigator.advanceProcess(instance, result.nextNodes, definition)
        executionEngine.executeNodes(result.nextNodes, instance, definition)
    }

    private fun startProcessInstanceFromMessageStart(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>?
    ): ProcessInstance? {
        val match = messageStartResolver.findMatch(messageName, correlationKey, variables) ?: return null
        val instance = startWithDefinition(match.definition, match.variables, match.startNode.get("id").asText())

        timelineService.record(
            processInstanceId = instance.id,
            nodeId = match.startNode.get("id").asText(),
            eventType = ProcessInstanceEventType.MESSAGE_RECEIVED,
            message = "Message '$messageName' started process with correlation key '$correlationKey'."
        )

        return instance
    }

    /**
     * Handle a message subscription timeout by routing to an attached ErrorBoundaryEvent
     * if present. Returns true if the timeout was handled by a boundary, false otherwise.
     */
    @Transactional
    fun handleSubscriptionTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val errorBoundaryNode = navigator.findAttachedErrorBoundary(node, definition)
        if (errorBoundaryNode != null) {
            val nextNodes = navigator.getNextNodes(errorBoundaryNode, definition, instance)
            navigator.advanceProcess(instance, nextNodes, definition)
            executionEngine.executeNodes(nextNodes, instance, definition)
            return true
        }

        return false
    }

    /**
     * Continue process execution after a TimerEvent timeout is reached.
     */
    @Transactional
    fun handleTimerTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        if (NodeType.fromString(node.get("type").asText()) != NodeType.TimerEvent) {
            return false
        }

        val nextNodes = navigator.getNextNodes(node, definition, instance)
        navigator.advanceProcess(instance, nextNodes, definition)
        executionEngine.executeNodes(nextNodes, instance, definition)
        return true
    }

    /* =========================
       JSON HELPERS
     ========================= */

    private fun parseDefinition(definitionJson: String): JsonNode =
        objectMapper.readTree(definitionJson)

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")

}
