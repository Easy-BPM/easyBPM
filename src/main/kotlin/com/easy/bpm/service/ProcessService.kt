package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.AdHocDecisionAudit
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.process.AdHocDecisionAuditRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import javax.script.ScriptEngineManager
import java.time.LocalDateTime

@Service
class ProcessService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val integrationService: IntegrationService,
    private val formService: FormService,
    private val taskRepository: TaskRepository,
    private val objectMapper: ObjectMapper,
    private val rabbitPublisher: com.easy.bpm.messaging.RabbitPublisher,
    private val gatewayService: GatewayService,
    private val messageSubscriptionService: MessageSubscriptionService,
    private val metricsService: MetricsService,
    private val workerRequestRepository: WorkerRequestRepository,
    private val callActivityHandler: CallActivityHandler,
    private val callActivityMappingRepository: CallActivityMappingRepository,
    private val adHocDecisionAuditRepository: AdHocDecisionAuditRepository
) {

    companion object {
        const val INTERNAL_TIMER_MESSAGE_NAME = "__internal.timer__"
        private val logger = LoggerFactory.getLogger(ProcessService::class.java)
    }

    private val processDefinitionSortableFields = setOf("id", "key", "name", "description", "version")
    private val processInstanceSortableFields = setOf("id", "status", "createdAt", "updatedAt")

    /* =========================
       DEPLOY
     ========================= */

    @Transactional
    fun deployProcess(definitionJson: JsonNode): ProcessDefinition {
        val json = validateAndParseDefinition(definitionJson)

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
    private fun startWithDefinition(definition: ProcessDefinition, initialVariables: Map<String, Any> = emptyMap()): ProcessInstance {
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

        initializeProcessVariables(instance, json)
        
        // Add initial variables if provided
        if (initialVariables.isNotEmpty()) {
            initialVariables.forEach { (name, value) ->
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = name,
                        value = objectMapper.valueToTree(value)
                    )
                )
            }
        }

        val startNodes = getStartNodes(instance, json)

        instance.currentNode = startNodes
        instance.nodeHistory = startNodes
        processInstanceRepository.save(instance)

        executeNodes(startNodes, instance, json)

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordProcessExecution(duration)

        return instance
    }

    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> =
        processInstanceRepository.findAll(sanitizeProcessInstancePageable(pageable))

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
    fun assignProcessVariables(processInstanceId: Long, variables: Map<String, Any?>): List<ProcessVariable> {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        variables.forEach { (name, value) ->
            val jsonValue = if (value == null) objectMapper.nullNode() else objectMapper.valueToTree(value)
            val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, name)

            if (existing != null) {
                existing.value = jsonValue
                processVariableRepository.save(existing)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = processInstanceId,
                        name = name,
                        value = jsonValue
                    )
                )
            }
        }

        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        return processVariableRepository.findByProcessInstanceId(processInstanceId)
    }

    @Transactional
    fun moveProcessNode(processInstanceId: Long, fromNode: String, toNode: String): ProcessInstance {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        val currentNodes = instance.currentNode ?: emptyList()
        if (!currentNodes.contains(fromNode)) {
            throw IllegalArgumentException("Instance is not currently at node '$fromNode'")
        }

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val toNodeDefinition = findNode(definition, toNode)

        syncTasksForManualMove(instance, fromNode, toNode, toNodeDefinition)

        val movedNodes = currentNodes.map { if (it == fromNode) toNode else it }
        instance.currentNode = movedNodes
        if (instance.nodeHistory.lastOrNull() != toNode) {
            instance.nodeHistory = instance.nodeHistory + toNode
        }
        instance.updatedAt = LocalDateTime.now()

        return processInstanceRepository.save(instance)
    }

    private fun syncTasksForManualMove(
        instance: ProcessInstance,
        fromNode: String,
        toNode: String,
        toNodeDefinition: JsonNode
    ) {
        val fromPendingTasks = taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
            instance.id,
            fromNode,
            TaskStatus.PENDING
        )

        fromPendingTasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
            taskRepository.delete(task)
        }

        val targetType = NodeType.fromString(toNodeDefinition.get("type").asText())
        if (targetType != NodeType.UserTask) {
            return
        }

        val existingTargetTasks = taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
            instance.id,
            toNode,
            TaskStatus.PENDING
        )

        if (existingTargetTasks.isEmpty()) {
            handleUserTask(instance, toNodeDefinition)
        }
    }

    fun getLatestProcessDefinitions(pageable: Pageable): Page<ProcessDefinition> =
        processDefinitionRepository.findLatestVersionProcesses(sanitizeProcessDefinitionPageable(pageable))

    fun getProcessDefinitionById(id: Long): ProcessDefinition? =
        processDefinitionRepository.findById(id).orElse(null)

    private fun sanitizeProcessDefinitionPageable(pageable: Pageable): Pageable {
        val sanitizedOrders = pageable.sort
            .filter { it.property in processDefinitionSortableFields }
            .toList()

        val effectiveSort = if (sanitizedOrders.isNotEmpty()) {
            Sort.by(sanitizedOrders)
        } else {
            Sort.by(Sort.Order.asc("key"), Sort.Order.desc("version"))
        }

        return if (pageable.isPaged) {
            PageRequest.of(pageable.pageNumber, pageable.pageSize, effectiveSort)
        } else {
            PageRequest.of(0, 100, effectiveSort)
        }
    }

    private fun sanitizeProcessInstancePageable(pageable: Pageable): Pageable {
        val sanitizedOrders = pageable.sort
            .filter { it.property in processInstanceSortableFields }
            .toList()

        val effectiveSort = if (sanitizedOrders.isNotEmpty()) {
            Sort.by(sanitizedOrders)
        } else {
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        }

        return if (pageable.isPaged) {
            PageRequest.of(pageable.pageNumber, pageable.pageSize, effectiveSort)
        } else {
            PageRequest.of(0, 100, effectiveSort)
        }
    }

    @Transactional
    fun stopProcessInstance(id: Long): ProcessInstance {
        val instance = processInstanceRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        if (instance.status != ProcessStatus.ACTIVE) {
            return instance
        }

        val pendingTasks = taskRepository.findByProcessInstanceId(id)
            .filter { it.status == TaskStatus.PENDING }
        pendingTasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
            taskRepository.delete(task)
        }

        messageSubscriptionService.deleteSubscriptionsForInstance(id)

        instance.status = ProcessStatus.CANCELLED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        return processInstanceRepository.save(instance)
    }

    @Transactional
    fun deleteProcessInstance(id: Long) {
        val instance = processInstanceRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        val tasks = taskRepository.findByProcessInstanceId(id)
        tasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
        }

        taskRepository.deleteByProcessInstanceId(id)
        processVariableRepository.deleteByProcessInstanceId(id)
        messageSubscriptionService.deleteSubscriptionsForInstance(id)
        workerRequestRepository.deleteByProcessInstanceId(id)
        processInstanceRepository.delete(instance)
    }

    /* =========================
       EXECUTION ENGINE CORE
     ========================= */

    private fun executeNodes(
        nodeIds: List<String>,
        instance: ProcessInstance,
        definition: JsonNode
    ) {
        for (nodeId in nodeIds) {
            val node = findNode(definition, nodeId)
            val handled = executeNode(instance, node, definition)
            if (handled) {
                // Error boundary was triggered and handled, stop further execution
                return
            }
        }
    }

    /**
     * Returns true if error boundary was triggered and handled, false otherwise.
     */
    private fun executeNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val nodeType = NodeType.fromString(node.get("type").asText())

        try {
            when (nodeType) {
                NodeType.UserTask -> handleUserTask(instance, node)
                NodeType.APITask -> handleAPITask(instance, node)
                NodeType.ServiceTask -> handleServiceTaskNode(instance, node, definition)
                NodeType.AdHocSubProcess -> handleAdHocSubProcess(instance, node, definition)
                NodeType.TimerEvent -> handleTimerEvent(instance, node)
                NodeType.MessageEvent -> handleMessageEvent(instance, node)
                NodeType.MessageIntermediateCatchEvent -> handleMessageIntermediateCatchEvent(instance, node)
                NodeType.MessageIntermediateThrowEvent -> handleMessageIntermediateThrowEvent(instance, node, definition)
                NodeType.CallActivity -> handleCallActivity(instance, node, definition)
                NodeType.EndEvent -> finishProcess(instance)
                NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                    val nextNodes = getNextNodes(node, definition, instance)
                    advanceProcess(instance, nextNodes, definition)
                    executeNodes(nextNodes, instance, definition)
                }
                else -> { /* no-op for other node types */ }
            }
        } catch (ex: Exception) {
            // Check for attached error boundary event
            val errorBoundaryNode = findAttachedErrorBoundary(node, definition)
            if (errorBoundaryNode != null) {
                // Capture error message and log for observability
                val errorMessage = ex.message ?: ex.javaClass.simpleName
                val errorCode = errorBoundaryNode.get("config")?.get("errorCode")?.asText() ?: "ERROR"
                val exceptionVariableName = errorBoundaryNode.get("config")?.get("exceptionVariable")?.asText()
                
                logger.warn("Error caught by boundary [$errorCode] on node ${node.get("id").asText()}: $errorMessage", ex)
                
                // Capture error message to process variable if exceptionVariable is mapped
                if (!exceptionVariableName.isNullOrBlank()) {
                    assignProcessVariables(instance.id, mapOf(exceptionVariableName to errorMessage))
                    logger.debug("Error message captured to variable '$exceptionVariableName'")
                }
                
                // Route to error boundary's next node(s) (do NOT execute the ErrorBoundaryEvent node itself)
                val nextNodes = getNextNodes(errorBoundaryNode, definition, instance)
                advanceProcess(instance, nextNodes, definition)
                println("DEBUG: error boundary nextNodes = $nextNodes")
                println("DEBUG: currentNode after advanceProcess = ${instance.currentNode}")
                executeNodes(nextNodes, instance, definition)
                val duration = System.currentTimeMillis() - startTime
                metricsService.recordNodeExecution(duration, nodeType.toString())
                return true // Signal to stop further execution
            } else {
                throw ex
            }
        }

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordNodeExecution(duration, nodeType.toString())
        return false
    }

    /**
     * Find an ErrorBoundaryEvent node attached to the given node, if any.
     */
    private fun findAttachedErrorBoundary(node: JsonNode, definition: JsonNode): JsonNode? {
        val nodeId = node.get("id").asText()
        val nodes = definition.get("nodes")
        return nodes.firstOrNull {
            NodeType.fromString(it.get("type").asText()) == NodeType.ErrorBoundaryEvent &&
            it.has("attachedTo") && it.get("attachedTo").asText() == nodeId
        }
    }

    private fun handleUserTask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val form = resolveUserTaskForm(node)
        val task = taskRepository.save(
            Task(
                processInstanceId = instance.id,
                title = node.get("name").asText(),
                nodeId = node.get("id").asText(),
                formId = form?.id
            )
        )

        metricsService.recordTaskCreated(task.nodeId)

        // Publish TaskCreated event
        try {
            rabbitPublisher.publishTaskCreated(
                mapOf(
                    "taskId" to task.id,
                    "processInstanceId" to task.processInstanceId,
                    "nodeId" to task.nodeId,
                    "title" to task.title,
                    "formDbId" to task.formId,
                    "formId" to form?.formId
                )
            )
        } catch (_: Exception) {
        }

        applyTaskInputs(task, node, instance)
    }

    private fun resolveUserTaskForm(node: JsonNode) =
        node.get("config")?.get("formId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredFormRef ->
            formService.getLatestVersionByFormId(configuredFormRef)
                ?: configuredFormRef.toLongOrNull()?.let(formService::getById)
                ?: formService.getLatestVersionByName(configuredFormRef)
        } ?: formService.getLatestVersionByName(node.get("id").asText())

    private fun handleAPITask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val config = node.get("properties")
            ?: node.get("service")
            ?: throw IllegalArgumentException("APITask ${node.get("id").asText()} missing properties/service")

        // Publish a service task request to RabbitMQ; worker will send a completion event.
        rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), config)

        // Persist instance updated timestamp; instance remains on this node until completion.
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun handleServiceTaskNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        var config = node.get("config")

        // Variable substitution for config fields
        if (config != null && config.isObject) {
            val configObj = (config.deepCopy() as com.fasterxml.jackson.databind.node.ObjectNode)
            configObj.fieldNames().forEachRemaining { field ->
                val valueNode = configObj.get(field)
                if (valueNode.isTextual && valueNode.asText().startsWith("\${") && valueNode.asText().endsWith("}")) {
                    val varName = valueNode.asText().removePrefix("\${").removeSuffix("}")
                    val variable = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
                    if (variable != null) {
                        configObj.replace(field, variable.value)
                    }
                }
            }
            config = configObj
        }

        // Simulate failure for error boundary test
        if (config != null && config.has("shouldFail")) {
            val shouldFailNode = config.get("shouldFail")
            val shouldFail = when {
                shouldFailNode.isBoolean -> shouldFailNode.asBoolean(false)
                shouldFailNode.isTextual -> shouldFailNode.asText().equals("true", ignoreCase = true)
                else -> false
            }
            if (shouldFail) {
                throw RuntimeException("Simulated service task failure for error boundary test")
            }
        }

        // If service task declares "variables", treat as internal (auto-execute)
        if (config != null && config.has("variables")) {
            val variables = config.get("variables")
            variables.forEach { varConfig ->
                val varName = varConfig.get("name")?.asText()
                    ?: throw IllegalArgumentException("ServiceTask variable missing 'name' field")

                val source = varConfig.get("source")?.asText() ?: "static"
                val value: JsonNode = when (source) {
                    "static" -> parseStaticValue(varConfig.get("value"))
                    "variable" -> {
                        val sourceVarName = varConfig.get("value")?.asText()
                            ?: throw IllegalArgumentException("ServiceTask variable missing source variable name")
                        val sourceVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, sourceVarName)
                            ?: throw IllegalArgumentException("Source variable '$sourceVarName' not found")
                        sourceVar.value
                    }
                    else -> throw IllegalArgumentException("Invalid variable source '$source'")
                }

                // Save or update process variable
                val existing = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
                if (existing != null) {
                    existing.value = value
                    processVariableRepository.save(existing)
                } else {
                    processVariableRepository.save(
                        ProcessVariable(
                            processInstanceId = instance.id,
                            name = varName,
                            value = value
                        )
                    )
                }
            }

            // Continue execution for internal service task
            val nextNodes = getNextNodes(node, definition, instance)
            advanceProcess(instance, nextNodes, definition)
            executeNodes(nextNodes, instance, definition)
        } else {
            // External service task: publish request and remain on this node until completion
            val properties = config ?: objectMapper.createObjectNode()
            try {
                rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), properties)
            } catch (_: Exception) {
            }

            instance.updatedAt = LocalDateTime.now()
            processInstanceRepository.save(instance)
        }
    }

    private fun handleMessageEvent(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        val properties = node.get("properties")
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing properties")

        val messageName = properties.get("messageName")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing messageName")

        val correlationKeyTemplate = properties.get("correlationKey")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing correlationKey")

        // Evaluate correlation key with variable substitution
        val correlationKey = evaluateCorrelationKey(correlationKeyTemplate, instance)

        val timeoutSeconds = properties.get("timeoutSeconds")?.asLong()

        // Create message subscription
        val timeoutAt = if (timeoutSeconds != null) {
            LocalDateTime.now().plusSeconds(timeoutSeconds)
        } else {
            null
        }

        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            timeoutAt = timeoutAt
        )

        // Publish message expected event
        try {
            rabbitPublisher.publishMessageExpected(
                processInstanceId = instance.id,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                timeoutSeconds = timeoutSeconds
            )
        } catch (_: Exception) {
        }

        // Persist instance updated timestamp; instance remains on this node until message arrival
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun handleTimerEvent(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        val properties = node.get("properties")
            ?: throw IllegalArgumentException("TimerEvent $nodeId missing properties")

        val timeoutSeconds = properties.get("timeoutSeconds")?.asLong()
            ?: throw IllegalArgumentException("TimerEvent $nodeId missing timeoutSeconds")

        require(timeoutSeconds > 0) { "TimerEvent $nodeId timeoutSeconds must be > 0" }

        val timeoutAt = LocalDateTime.now().plusSeconds(timeoutSeconds)

        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = INTERNAL_TIMER_MESSAGE_NAME,
            correlationKey = "timer-${instance.id}-$nodeId",
            timeoutAt = timeoutAt
        )

        // Instance remains on this node until timer timeout is processed by scheduler
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun handleMessageIntermediateCatchEvent(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        val message = node.get("message")
            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message' object")

        val messageName = message.get("name")?.asText()
            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message.correlationKeys'")
        }

        val correlationKey = correlationKeys[0].asText()

        // Create message subscription with payload mapping
        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            timeoutAt = null
        )

        // Publish message expected event
        try {
            rabbitPublisher.publishMessageExpected(
                processInstanceId = instance.id,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                timeoutSeconds = null
            )
        } catch (_: Exception) {
        }

        // Persist instance updated timestamp; instance remains on this node until message arrival
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun handleMessageIntermediateThrowEvent(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        val message = node.get("message")
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message' object")

        val messageName = message.get("name")?.asText()
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message.correlationKeys'")
        }

        val correlationKey = correlationKeys[0].asText()

        // Build payload from node configuration
        val payloadArray = message.get("payload")
        val payload = mutableMapOf<String, Any>()

        if (payloadArray != null && payloadArray.isArray) {
            payloadArray.forEach { payloadMapping ->
                val targetName = payloadMapping.get("targetName")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'targetName'")

                val source = payloadMapping.get("source")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'source'")

                val value = payloadMapping.get("value")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'value'")

                // Map from process variable or static value
                val mappedValue = when (source) {
                    "variable" -> {
                        processVariableRepository.findByProcessInstanceIdAndName(instance.id, value)
                            ?.value?.asText() ?: value
                    }

                    "static" -> value
                    else -> value
                }

                payload[targetName] = mappedValue
            }
        }

        // Publish message to external system
        try {
            rabbitPublisher.publishMessageThrown(
                messageName = messageName,
                correlationKey = correlationKey,
                variables = payload
            )
        } catch (ex: Exception) {
            // Log exception but continue
        }

        // Advance to next node
        val nextNodes = getNextNodes(node, definition, instance)
        advanceProcess(instance, nextNodes, definition)
        executeNodes(nextNodes, instance, definition)
    }

    /**
     * Handle Call Activity (Subprocess) node execution.
     * Creates a child process instance and manages parent-child relationship.
     */
    private fun handleCallActivity(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        try {
            callActivityHandler.executeCallActivity(instance, node, definition)
            logger.info("Call activity node '${node.get("id").asText()}' executed successfully")
        } catch (ex: Exception) {
            logger.error("Error executing call activity node", ex)
            throw ex
        }
    }

    private fun handleAdHocSubProcess(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        val activities = getAdHocActivities(node)
        val state = ensureAdHocState(instance.id, nodeId, activities)
        syncAdHocCounters(instance.id, nodeId, state)

        val completionCondition = node.get("config")?.get("completionCondition")?.asText()?.trim()
        val shouldComplete = when {
            !completionCondition.isNullOrBlank() -> evaluateCondition(completionCondition, instance)
            else -> {
                val total = (state["activities"] as? List<*>)?.size ?: 0
                val completed = (state["completed"] as? List<*>)?.size ?: 0
                val skipped = (state["skipped"] as? List<*>)?.size ?: 0
                total > 0 && (completed + skipped) >= total
            }
        }

        if (shouldComplete) {
            appendAdHocAudit(
                processInstanceId = instance.id,
                adHocNodeId = nodeId,
                decisionType = "COMPLETION_CONDITION_SATISFIED",
                actorType = "system",
                details = mapOf("state" to state)
            )
            val nextNodes = getNextNodes(node, definition, instance)
            advanceProcess(instance, nextNodes, definition)
            executeNodes(nextNodes, instance, definition)
            return
        }

        instance.currentNode = listOf(nodeId)
        if (instance.nodeHistory.lastOrNull() != nodeId) {
            instance.nodeHistory = instance.nodeHistory + nodeId
        }
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    @Transactional
    fun getAdHocContext(processInstanceId: Long, adHocNodeId: String): Map<String, Any?> {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val adHocNode = findNode(definition, adHocNodeId)
        if (NodeType.fromString(adHocNode.get("type").asText()) != NodeType.AdHocSubProcess) {
            throw IllegalArgumentException("Node '$adHocNodeId' is not an AdHocSubProcess")
        }

        val activities = getAdHocActivities(adHocNode)
        val state = ensureAdHocState(processInstanceId, adHocNodeId, activities)
        syncAdHocCounters(processInstanceId, adHocNodeId, state)
        val eligible = getEligibleAdHocActivities(adHocNode, definition, instance, state)
        val variables = processVariableRepository.findByProcessInstanceId(processInstanceId)
            .associate { it.name to objectMapper.convertValue(it.value, Any::class.java) }
        val audit = adHocDecisionAuditRepository
            .findByProcessInstanceIdAndAdHocNodeIdOrderByCreatedAtDesc(processInstanceId, adHocNodeId)
            .take(50)

        return mapOf(
            "processInstanceId" to processInstanceId,
            "adHocNodeId" to adHocNodeId,
            "currentNode" to instance.currentNode,
            "activities" to activities,
            "eligibleActivities" to eligible,
            "state" to state,
            "variables" to variables,
            "auditEvents" to audit
        )
    }

    @Transactional
    fun submitAdHocDecision(processInstanceId: Long, adHocNodeId: String, decision: Map<String, Any?>): Map<String, Any?> {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val adHocNode = findNode(definition, adHocNodeId)
        if (NodeType.fromString(adHocNode.get("type").asText()) != NodeType.AdHocSubProcess) {
            throw IllegalArgumentException("Node '$adHocNodeId' is not an AdHocSubProcess")
        }

        val activities = getAdHocActivities(adHocNode)
        var state = ensureAdHocState(processInstanceId, adHocNodeId, activities)

        val decisionType = (decision["decisionType"] as? String)?.trim()?.uppercase()
            ?: throw IllegalArgumentException("decisionType is required")
        val actorId = (decision["agentId"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
            ?: (decision["actorId"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
        val actorType = (decision["actorType"] as? String)?.trim()?.lowercase().takeUnless { it.isNullOrBlank() }
            ?: if (actorId != null) "agent" else "system"
        val recommendation = decision["recommendation"] as? String
        val confidence = when (val raw = decision["confidence"]) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }

        val activityId = (decision["activityId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        if (activityId != null && activityId !in activities) {
            throw IllegalArgumentException("Activity '$activityId' is not part of ad-hoc node '$adHocNodeId'")
        }

        when (decisionType) {
            "RECOMMENDATION", "ESCALATE_TO_HUMAN", "COLLABORATE" -> {
                // Audit only decisions.
            }
            "ACTIVATE_ACTIVITY", "SELECT_NEXT_ACTIVITY" -> {
                if (activityId == null) throw IllegalArgumentException("activityId is required for $decisionType")
                val eligible = getEligibleAdHocActivities(adHocNode, definition, instance, state)
                if (activityId !in eligible) {
                    throw IllegalArgumentException("Activity '$activityId' is not eligible")
                }
                state = markAdHocActivityActivated(processInstanceId, adHocNodeId, activityId, state)
                executeAdHocActivity(instance, adHocNodeId, activityId, definition)
            }
            "SKIP_ACTIVITY" -> {
                if (activityId == null) throw IllegalArgumentException("activityId is required for SKIP_ACTIVITY")
                state = markAdHocActivitySkipped(processInstanceId, adHocNodeId, activityId, state)
            }
            "REPEAT_ACTIVITY" -> {
                if (activityId == null) throw IllegalArgumentException("activityId is required for REPEAT_ACTIVITY")
                state = markAdHocActivityRequeued(processInstanceId, adHocNodeId, activityId, state)
                executeAdHocActivity(instance, adHocNodeId, activityId, definition)
            }
            else -> throw IllegalArgumentException("Unsupported decisionType '$decisionType'")
        }

        appendAdHocAudit(
            processInstanceId = processInstanceId,
            adHocNodeId = adHocNodeId,
            activityNodeId = activityId,
            decisionType = decisionType,
            actorType = actorType,
            actorId = actorId,
            confidence = confidence,
            recommendation = recommendation,
            details = decision
        )

        val refreshedState = getAdHocState(processInstanceId, adHocNodeId) ?: state
        syncAdHocCounters(processInstanceId, adHocNodeId, refreshedState)
        handleAdHocSubProcess(instance, adHocNode, definition)
        return getAdHocContext(processInstanceId, adHocNodeId)
    }

    @Transactional
    fun onAdHocActivityCompleted(processInstanceId: Long, adHocNodeId: String, activityNodeId: String) {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val adHocNode = findNode(definition, adHocNodeId)
        var state = getAdHocState(processInstanceId, adHocNodeId)
            ?: ensureAdHocState(processInstanceId, adHocNodeId, getAdHocActivities(adHocNode))
        state = markAdHocActivityCompleted(processInstanceId, adHocNodeId, activityNodeId, state)

        appendAdHocAudit(
            processInstanceId = processInstanceId,
            adHocNodeId = adHocNodeId,
            activityNodeId = activityNodeId,
            decisionType = "ACTIVITY_COMPLETED",
            actorType = "system",
            details = mapOf("state" to state)
        )

        val refreshed = processInstanceRepository.findById(processInstanceId).orElseThrow()
        handleAdHocSubProcess(refreshed, adHocNode, definition)
    }

    @Transactional
    fun evaluateAdHocNode(processInstanceId: Long, adHocNodeId: String) {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val adHocNode = findNode(definition, adHocNodeId)
        if (NodeType.fromString(adHocNode.get("type").asText()) != NodeType.AdHocSubProcess) {
            throw IllegalArgumentException("Node '$adHocNodeId' is not an AdHocSubProcess")
        }
        handleAdHocSubProcess(instance, adHocNode, definition)
    }

    fun findAdHocParentForActivity(processInstanceId: Long, activityNodeId: String): String? {
        val link = processVariableRepository.findByProcessInstanceIdAndName(
            processInstanceId,
            adHocActivityParentVariableName(activityNodeId)
        ) ?: return null
        return if (link.value.isTextual) link.value.asText() else null
    }

    @Transactional
    fun handleServiceTaskCompleted(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val startTime = System.currentTimeMillis()
        
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        // Save or update outputs as process variables
        outputs.forEach { (k, v) ->
            val value = objectMapper.readTree(v)
            val existing = processVariableRepository.findByProcessInstanceIdAndName(instance.id, k)
            
            if (existing != null) {
                existing.value = value
                processVariableRepository.save(existing)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = k,
                        value = value
                    )
                )
            }
        }

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val nextNodes = getNextNodes(node, definition, instance)

        advanceProcess(instance, nextNodes, definition)

        executeNodes(nextNodes, instance, definition)
        
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordServiceTaskExecution(duration, success = true)
    }

    @Transactional
    fun handleServiceTaskFailed(processInstanceId: Long, nodeId: String, errorMessage: String? = null) {
        val startTime = System.currentTimeMillis()

        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val errorBoundaryNode = findAttachedErrorBoundary(node, definition)
        if (errorBoundaryNode != null) {
            val nextNodes = getNextNodes(errorBoundaryNode, definition, instance)
            advanceProcess(instance, nextNodes, definition)
            executeNodes(nextNodes, instance, definition)

            val duration = System.currentTimeMillis() - startTime
            metricsService.recordServiceTaskExecution(duration, success = false)
            return
        }

        // No boundary to recover from this failure; mark instance as failed.
        instance.status = ProcessStatus.FAILED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordServiceTaskExecution(duration, success = false)
    }

    @Transactional
    fun handleMessageReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ) {
        metricsService.recordMessageEventReceived(messageName)
        
        // Find matching message subscription
        val subscription = messageSubscriptionService.receiveMessage(
            messageName,
            correlationKey,
            variables
        ) ?: throw IllegalArgumentException("No waiting subscription for message '$messageName' with correlationKey '$correlationKey'")

        val instance = processInstanceRepository.findById(subscription.processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance ${subscription.processInstanceId} not found") }

        // Save received message variables as process variables
        variables?.forEach { (k, v) ->
            val value = objectMapper.convertValue(v, com.fasterxml.jackson.databind.JsonNode::class.java)
            val existing = processVariableRepository.findByProcessInstanceIdAndName(instance.id, k)
            
            if (existing != null) {
                existing.value = value
                processVariableRepository.save(existing)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = k,
                        value = value
                    )
                )
            }
        }

        // Clean up subscription after message received
        messageSubscriptionService.deleteSubscription(subscription.id)

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, subscription.nodeId)

        val nextNodes = getNextNodes(node, definition, instance)

        advanceProcess(instance, nextNodes, definition)

        executeNodes(nextNodes, instance, definition)
    }

    /**
     * Handle a message subscription timeout by routing to an attached ErrorBoundaryEvent
     * if present. Returns true if the timeout was handled by a boundary, false otherwise.
     */
    @Transactional
    fun handleSubscriptionTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findById(processInstanceId).orElse(null) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val errorBoundaryNode = findAttachedErrorBoundary(node, definition)
        if (errorBoundaryNode != null) {
            val nextNodes = getNextNodes(errorBoundaryNode, definition, instance)
            advanceProcess(instance, nextNodes, definition)
            executeNodes(nextNodes, instance, definition)
            return true
        }

        return false
    }

    /**
     * Continue process execution after a TimerEvent timeout is reached.
     */
    @Transactional
    fun handleTimerTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findById(processInstanceId).orElse(null) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        if (NodeType.fromString(node.get("type").asText()) != NodeType.TimerEvent) {
            return false
        }

        val nextNodes = getNextNodes(node, definition, instance)
        advanceProcess(instance, nextNodes, definition)
        executeNodes(nextNodes, instance, definition)
        return true
    }

    private fun finishProcess(instance: ProcessInstance) {
        instance.status = ProcessStatus.COMPLETED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
        
        metricsService.recordProcessCompleted()
    }

    private fun advanceProcess(
        instance: ProcessInstance,
        nextNodes: List<String>,
        definition: JsonNode
    ) {
        instance.currentNode = nextNodes

        // Resolve and append meaningful nodes to history (skip gateways, but include their resolved targets)
        val appendable = mutableListOf<String>()

        fun resolveAndCollect(id: String) {
            val node = definition.get("nodes").find { it.get("id").asText() == id } ?: return
            when (NodeType.fromString(node.get("type").asText())) {
                NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                    val targets = getNextNodes(node, definition, instance)
                    targets.forEach { resolveAndCollect(it) }
                }
                NodeType.UserTask,
                NodeType.ServiceTask,
                NodeType.APITask,
                NodeType.MessageEvent,
                NodeType.MessageIntermediateCatchEvent,
                NodeType.MessageIntermediateThrowEvent,
                NodeType.TimerEvent,
                NodeType.ScriptTask,
                NodeType.EndEvent -> appendable.add(id)
                else -> { /* ignore other node types */ }
            }
        }

        nextNodes.forEach { resolveAndCollect(it) }

        // Avoid consecutive duplicates when appending
        appendable.forEach { id ->
            if (instance.nodeHistory.lastOrNull() != id) instance.nodeHistory = instance.nodeHistory + id
        }
        instance.updatedAt = LocalDateTime.now()

        // Process is complete if:
        // 1. No more nodes to execute, OR
        // 2. All next nodes are EndEvent nodes
        val isCompleted = nextNodes.isEmpty() || nextNodes.all { nodeId ->
            val node = definition.get("nodes").find { it.get("id").asText() == nodeId }
            node != null && NodeType.fromString(node.get("type").asText()) == NodeType.EndEvent
        }
        
        if (isCompleted) {
            instance.status = ProcessStatus.COMPLETED
            instance.currentNode = emptyList()
        }

        processInstanceRepository.save(instance)
    }

    /* =========================
       VARIABLE MANAGEMENT
     ========================= */

    private fun initializeProcessVariables(
        instance: ProcessInstance,
        definition: JsonNode
    ) {
        val variablesNode = definition.get("variables") ?: return

        val variables = variablesNode.map {
            ProcessVariable(
                processInstanceId = instance.id,
                name = it.get("name").asText(),
                value = it.get("initialValue") ?: objectMapper.nullNode()
            )
        }

        if (variables.isNotEmpty()) {
            processVariableRepository.saveAll(variables)
        }
    }

    private fun applyTaskInputs(
        task: Task,
        node: JsonNode,
        instance: ProcessInstance
    ) {
        val inputs = node.get("config")?.get("inputs") ?: return

        inputs.forEach { input ->
            val targetName = input.get("targetName").asText()
            val source = input.get("source").asText()
            val valueNode = input.get("value")

            val value: JsonNode = when (source) {
                "variable" -> {
                    val varName = valueNode.asText()
                    val processVar =
                        processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
                            ?: throw IllegalArgumentException("Process variable '$varName' not found")
                    processVar.value
                }

                "static" -> parseStaticValue(valueNode)

                else -> throw IllegalArgumentException("Invalid input source '$source'")
            }

            taskVariableRepository.save(
                TaskVariable(
                    taskId = task.id,
                    name = targetName,
                    value = value
                )
            )
        }
    }

    private fun getAdHocActivities(node: JsonNode): List<String> {
        val config = node.get("config")
            ?: throw IllegalArgumentException("AdHocSubProcess ${node.get("id").asText()} missing config")
        val activitiesNode = config.get("activities")
            ?: throw IllegalArgumentException("AdHocSubProcess ${node.get("id").asText()} missing config.activities")
        require(activitiesNode.isArray) { "AdHocSubProcess ${node.get("id").asText()} config.activities must be an array" }
        val activities = activitiesNode.mapNotNull { it.asText()?.trim() }.filter { it.isNotBlank() }.distinct()
        require(activities.isNotEmpty()) { "AdHocSubProcess ${node.get("id").asText()} must define at least one activity" }
        return activities
    }

    private fun adHocStateVariableName(adHocNodeId: String) = "__adhoc_state__$adHocNodeId"
    private fun adHocActivityParentVariableName(activityNodeId: String) = "__adhoc_activity_parent__$activityNodeId"
    private fun adHocCompletedCountVariableName(adHocNodeId: String) = "__adhoc_completed_count__$adHocNodeId"
    private fun adHocSkippedCountVariableName(adHocNodeId: String) = "__adhoc_skipped_count__$adHocNodeId"
    private fun adHocActiveCountVariableName(adHocNodeId: String) = "__adhoc_active_count__$adHocNodeId"

    private fun getAdHocState(processInstanceId: Long, adHocNodeId: String): MutableMap<String, Any?>? {
        val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, adHocStateVariableName(adHocNodeId))
            ?: return null
        val value = objectMapper.convertValue(existing.value, MutableMap::class.java)
        @Suppress("UNCHECKED_CAST")
        return value as MutableMap<String, Any?>
    }

    private fun ensureAdHocState(
        processInstanceId: Long,
        adHocNodeId: String,
        activities: List<String>
    ): MutableMap<String, Any?> {
        val existing = getAdHocState(processInstanceId, adHocNodeId)
        if (existing != null) return existing

        val state = mutableMapOf<String, Any?>(
            "activities" to activities,
            "active" to emptyList<String>(),
            "completed" to emptyList<String>(),
            "skipped" to emptyList<String>(),
            "repeatCounts" to emptyMap<String, Int>()
        )
        saveAdHocState(processInstanceId, adHocNodeId, state)
        return state
    }

    private fun saveAdHocState(processInstanceId: Long, adHocNodeId: String, state: Map<String, Any?>) {
        val name = adHocStateVariableName(adHocNodeId)
        val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, name)
        val value = objectMapper.valueToTree<JsonNode>(state)
        if (existing != null) {
            existing.value = value
            processVariableRepository.save(existing)
        } else {
            processVariableRepository.save(
                ProcessVariable(
                    processInstanceId = processInstanceId,
                    name = name,
                    value = value
                )
            )
        }
    }

    private fun markAdHocActivityActivated(
        processInstanceId: Long,
        adHocNodeId: String,
        activityNodeId: String,
        state: MutableMap<String, Any?>
    ): MutableMap<String, Any?> {
        val active = ((state["active"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        val skipped = ((state["skipped"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        active.add(activityNodeId)
        skipped.remove(activityNodeId)
        state["active"] = active.toList()
        state["skipped"] = skipped.toList()
        saveAdHocState(processInstanceId, adHocNodeId, state)
        persistAdHocActivityParentLink(processInstanceId, activityNodeId, adHocNodeId)
        return state
    }

    private fun markAdHocActivityCompleted(
        processInstanceId: Long,
        adHocNodeId: String,
        activityNodeId: String,
        state: MutableMap<String, Any?>
    ): MutableMap<String, Any?> {
        val active = ((state["active"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        val completed = ((state["completed"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        active.remove(activityNodeId)
        completed.add(activityNodeId)
        state["active"] = active.toList()
        state["completed"] = completed.toList()
        saveAdHocState(processInstanceId, adHocNodeId, state)
        return state
    }

    private fun markAdHocActivitySkipped(
        processInstanceId: Long,
        adHocNodeId: String,
        activityNodeId: String,
        state: MutableMap<String, Any?>
    ): MutableMap<String, Any?> {
        val active = ((state["active"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        val skipped = ((state["skipped"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        active.remove(activityNodeId)
        skipped.add(activityNodeId)
        state["active"] = active.toList()
        state["skipped"] = skipped.toList()
        saveAdHocState(processInstanceId, adHocNodeId, state)
        return state
    }

    private fun markAdHocActivityRequeued(
        processInstanceId: Long,
        adHocNodeId: String,
        activityNodeId: String,
        state: MutableMap<String, Any?>
    ): MutableMap<String, Any?> {
        val completed = ((state["completed"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        val skipped = ((state["skipped"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        val active = ((state["active"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).toMutableSet()
        completed.remove(activityNodeId)
        skipped.remove(activityNodeId)
        active.add(activityNodeId)
        @Suppress("UNCHECKED_CAST")
        val repeatCounts = ((state["repeatCounts"] as? Map<*, *>) ?: emptyMap<Any, Any>())
            .mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = when (v) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull()
                    else -> 0
                } ?: 0
                key to value
            }
            .toMap()
            .toMutableMap()
        repeatCounts[activityNodeId] = (repeatCounts[activityNodeId] ?: 0) + 1
        state["completed"] = completed.toList()
        state["skipped"] = skipped.toList()
        state["active"] = active.toList()
        state["repeatCounts"] = repeatCounts
        saveAdHocState(processInstanceId, adHocNodeId, state)
        persistAdHocActivityParentLink(processInstanceId, activityNodeId, adHocNodeId)
        return state
    }

    private fun persistAdHocActivityParentLink(processInstanceId: Long, activityNodeId: String, adHocNodeId: String) {
        val name = adHocActivityParentVariableName(activityNodeId)
        val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, name)
        val value = objectMapper.valueToTree<JsonNode>(adHocNodeId)
        if (existing != null) {
            existing.value = value
            processVariableRepository.save(existing)
        } else {
            processVariableRepository.save(
                ProcessVariable(
                    processInstanceId = processInstanceId,
                    name = name,
                    value = value
                )
            )
        }
    }

    private fun syncAdHocCounters(processInstanceId: Long, adHocNodeId: String, state: Map<String, Any?>) {
        val completedCount = ((state["completed"] as? List<*>)?.size ?: 0)
        val skippedCount = ((state["skipped"] as? List<*>)?.size ?: 0)
        val activeCount = ((state["active"] as? List<*>)?.size ?: 0)
        assignProcessVariables(
            processInstanceId,
            mapOf(
                adHocCompletedCountVariableName(adHocNodeId) to completedCount,
                adHocSkippedCountVariableName(adHocNodeId) to skippedCount,
                adHocActiveCountVariableName(adHocNodeId) to activeCount
            )
        )
    }

    private fun getEligibleAdHocActivities(
        adHocNode: JsonNode,
        definition: JsonNode,
        instance: ProcessInstance,
        state: Map<String, Any?>
    ): List<String> {
        val activities = getAdHocActivities(adHocNode)
        val active = ((state["active"] as? List<*>)?.mapNotNull { it as? String } ?: emptySet())
        val completed = ((state["completed"] as? List<*>)?.mapNotNull { it as? String } ?: emptySet())
        val skipped = ((state["skipped"] as? List<*>)?.mapNotNull { it as? String } ?: emptySet())
        val eligibilityNode = adHocNode.get("config")?.get("eligibility")

        return activities.filter { activityId ->
            if (activityId in active) return@filter false
            if (activityId in completed) return@filter false
            if (activityId in skipped) return@filter false
            val activity = findNode(definition, activityId)
            if (NodeType.fromString(activity.get("type").asText()) != NodeType.UserTask) {
                return@filter false
            }
            val expression = eligibilityNode?.get(activityId)?.asText()?.trim()
            if (expression.isNullOrBlank()) true else evaluateCondition(expression, instance)
        }
    }

    private fun executeAdHocActivity(
        instance: ProcessInstance,
        adHocNodeId: String,
        activityNodeId: String,
        definition: JsonNode
    ) {
        val node = findNode(definition, activityNodeId)
        val nodeType = NodeType.fromString(node.get("type").asText())
        if (nodeType != NodeType.UserTask) {
            throw IllegalArgumentException("Ad-hoc activity '$activityNodeId' must be a HumanTask/UserTask")
        }
        val existingPending = taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
            instance.id,
            activityNodeId,
            TaskStatus.PENDING
        )
        if (existingPending.isEmpty()) {
            handleUserTask(instance, node)
        }
        instance.currentNode = listOf(adHocNodeId)
        if (instance.nodeHistory.lastOrNull() != adHocNodeId) {
            instance.nodeHistory = instance.nodeHistory + adHocNodeId
        }
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun appendAdHocAudit(
        processInstanceId: Long,
        adHocNodeId: String,
        decisionType: String,
        actorType: String,
        activityNodeId: String? = null,
        actorId: String? = null,
        confidence: Double? = null,
        recommendation: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        adHocDecisionAuditRepository.save(
            AdHocDecisionAudit(
                processInstanceId = processInstanceId,
                adHocNodeId = adHocNodeId,
                activityNodeId = activityNodeId,
                decisionType = decisionType,
                actorType = actorType,
                actorId = actorId,
                confidence = confidence,
                recommendation = recommendation,
                details = details
            )
        )
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

    private fun getNextNodes(node: JsonNode, definition: JsonNode, instance: ProcessInstance): List<String> {
        return gatewayService.getNextNodes(node, definition, instance)
    }

    private fun getStartNodes(instance: ProcessInstance, definition: JsonNode): List<String> {
        val start = definition.get("nodes")
            .find { NodeType.fromString(it.get("type").asText()) == NodeType.StartEvent }
            ?: throw IllegalArgumentException("StartEvent not found")

        return getNextNodes(start, definition, instance).also {
            if (it.isEmpty()) {
                throw IllegalArgumentException("StartEvent has no outgoing flow")
            }
        }
    }

    private fun evaluateCondition(condition: String, instance: ProcessInstance): Boolean {
        return gatewayService.evaluateCondition(condition, instance)
    }

    /**
     * Evaluate correlation key template with variable substitution.
     * Supports ${variableName} expressions like ${orderId}-${instanceNumber}
     */
    private fun evaluateCorrelationKey(template: String, instance: ProcessInstance): String {
        var result = template
        val regex = Regex("\\$\\{([^}]+)\\}")

        result = regex.replace(result) { match ->
            val varName = match.groupValues[1]
            val processVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
            when {
                processVar == null || processVar.value.isNull -> varName // Fallback to var name if not found
                processVar.value.isTextual -> processVar.value.asText()
                processVar.value.isNumber -> processVar.value.toString()
                else -> processVar.value.toString()
            }
        }

        return result
    }

    private fun parseStaticValue(valueNode: JsonNode?): JsonNode {
        if (valueNode == null || valueNode.isNull) return objectMapper.nullNode()

        if (!valueNode.isTextual) return valueNode

        val text = valueNode.asText()
        val trimmed = text.trim()

        // Try to parse textual content as JSON when it looks like JSON (object/array/literal/number).
        // If parse fails or it doesn't look like JSON, return as a plain text node.
        return try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[") ||
                trimmed == "null" || trimmed == "true" || trimmed == "false" ||
                trimmed.matches(Regex("-?\\d+(\\.\\d+)?"))
            ) {
                objectMapper.readTree(text)
            } else {
                objectMapper.nodeFactory.textNode(text)
            }
        } catch (ex: Exception) {
            objectMapper.nodeFactory.textNode(text)
        }
    }

    /* =========================
       DEPLOY VALIDATION
     ========================= */

    private fun validateAndParseDefinition(definitionJson: JsonNode): JsonNode {
        val json = definitionJson.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Root JSON must be an object")

        json.get("processId") ?: throw IllegalArgumentException("Missing 'processId'")

        val nodes = json.get("nodes")
            ?: throw IllegalArgumentException("Missing 'nodes'")
        require(nodes.isArray) { "'nodes' must be an array" }

        val flows = json.get("flows")
            ?: throw IllegalArgumentException("Missing 'flows'")
        require(flows.isArray) { "'flows' must be an array" }

        val nodeIds = mutableSetOf<String>()
        val nodeTypesById = mutableMapOf<String, NodeType>()

        nodes.forEach { node ->
            val id = node.get("id")?.asText()
                ?: throw IllegalArgumentException("Node missing 'id'")

            val typeText = node.get("type")?.asText()
                ?: throw IllegalArgumentException("Node $id missing 'type'")

            val nodeType = try {
                NodeType.fromString(typeText)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid node type '$typeText' at '$id'")
            }

            // Validate MessageEvent properties
            if (nodeType == NodeType.MessageEvent) {
                val properties = node.get("properties")
                    ?: throw IllegalArgumentException("MessageEvent $id missing 'properties'")

                properties.get("messageName")?.asText()
                    ?: throw IllegalArgumentException("MessageEvent $id missing 'messageName' in properties")

                properties.get("correlationKey")?.asText()
                    ?: throw IllegalArgumentException("MessageEvent $id missing 'correlationKey' in properties")
            }

            // Validate MessageIntermediateCatchEvent properties
            if (nodeType == NodeType.MessageIntermediateCatchEvent) {
                val message = node.get("message")
                    ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id missing 'message' object")

                message.get("name")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id missing 'message.name'")

                val correlationKeys = message.get("correlationKeys")
                if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
                    throw IllegalArgumentException("MessageIntermediateCatchEvent $id missing or empty 'message.correlationKeys'")
                }

                val payload = message.get("payload")
                if (payload != null && payload.isArray) {
                    payload.forEach { mapping ->
                        mapping.get("sourceName")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'sourceName'")
                        mapping.get("target")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'target'")
                        mapping.get("value")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'value'")
                    }
                }
            }

            // Validate MessageIntermediateThrowEvent properties
            if (nodeType == NodeType.MessageIntermediateThrowEvent) {
                val message = node.get("message")
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing 'message' object")

                message.get("name")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing 'message.name'")

                val correlationKeys = message.get("correlationKeys")
                if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
                    throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing or empty 'message.correlationKeys'")
                }

                val payload = message.get("payload")
                if (payload != null && payload.isArray) {
                    payload.forEach { mapping ->
                        mapping.get("targetName")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'targetName'")
                        mapping.get("source")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'source'")
                        mapping.get("value")?.asText()
                            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'value'")
                    }
                }
            }

            // Validate ServiceTask properties
            if (nodeType == NodeType.APITask) {
                val properties = node.get("properties") ?: node.get("service")
                    ?: throw IllegalArgumentException("APITask $id missing 'properties' or legacy 'service'")

                if (node.get("properties") == null && node.get("service") != null && node is com.fasterxml.jackson.databind.node.ObjectNode) {
                    node.set<JsonNode>("properties", node.get("service"))
                }

                val url = properties.get("url")?.asText()?.trim()
                    ?: throw IllegalArgumentException("APITask $id missing 'url' in properties")
                if (url.isEmpty()) {
                    throw IllegalArgumentException("APITask $id has empty 'url' in properties")
                }

                val auth = properties.get("auth")
                if (auth != null && !auth.isNull) {
                    if (!auth.isObject) {
                        throw IllegalArgumentException("APITask $id has invalid 'auth' format")
                    }

                    val authType = auth.get("type")?.asText()?.trim()?.lowercase()
                        ?: throw IllegalArgumentException("APITask $id auth missing 'type'")
                    if (authType !in setOf("bearer", "basic", "apikey")) {
                        throw IllegalArgumentException("APITask $id auth.type '$authType' is unsupported")
                    }

                    val authRef = auth.get("ref")?.asText()?.trim()
                        ?: throw IllegalArgumentException("APITask $id auth missing 'ref'")
                    if (authRef.isEmpty()) {
                        throw IllegalArgumentException("APITask $id auth.ref cannot be blank")
                    }

                    if (authType == "apikey") {
                        val target = auth.get("in")?.asText()?.trim()?.lowercase() ?: "header"
                        if (target !in setOf("header", "query")) {
                            throw IllegalArgumentException("APITask $id auth.in must be 'header' or 'query'")
                        }

                        val keyName = auth.get("key")?.asText()?.trim() ?: "X-API-Key"
                        if (keyName.isEmpty()) {
                            throw IllegalArgumentException("APITask $id auth.key cannot be blank")
                        }
                    }
                }
            }

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }
            nodeTypesById[id] = nodeType
        }

        nodes.forEach { node ->
            val id = node.get("id").asText()
            val type = NodeType.fromString(node.get("type").asText())
            if (type != NodeType.AdHocSubProcess) return@forEach

            val config = node.get("config")
                ?: throw IllegalArgumentException("AdHocSubProcess $id missing 'config'")
            val activities = config.get("activities")
                ?: throw IllegalArgumentException("AdHocSubProcess $id missing 'config.activities'")
            if (!activities.isArray || activities.size() == 0) {
                throw IllegalArgumentException("AdHocSubProcess $id must define non-empty 'config.activities' array")
            }

            activities.forEach { activity ->
                val activityId = activity.asText()
                if (!nodeIds.contains(activityId)) {
                    throw IllegalArgumentException("AdHocSubProcess $id references unknown activity '$activityId'")
                }
                val activityType = nodeTypesById[activityId]
                    ?: throw IllegalArgumentException("AdHocSubProcess $id activity '$activityId' missing type")
                if (activityType != NodeType.UserTask) {
                    throw IllegalArgumentException("AdHocSubProcess $id activity '$activityId' must be HumanTask/UserTask")
                }
            }
        }

        return json
    }
}
