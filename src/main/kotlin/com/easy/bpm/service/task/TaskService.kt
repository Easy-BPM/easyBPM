package com.easy.bpm.service.task

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.controller.data.TaskResponseDto
import com.easy.bpm.handler.AgentProcessCallHandler
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.HistoricTaskVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.integration.IntegrationService
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.GatewayService
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.variable.HistoricVariableArchiver
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDateTime
import javax.script.ScriptEngineManager

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val historicTaskVariableRepository: HistoricTaskVariableRepository,
    private val integrationService: IntegrationService,
    private val formService: FormService,
    private val objectMapper: ObjectMapper
        ,
    private val rabbitPublisher: com.easy.bpm.messaging.RabbitPublisher,
    private val gatewayService: GatewayService,
    private val messageSubscriptionService: MessageSubscriptionService,
    private val metricsService: MetricsService,
    private val agentProcessCallHandler: AgentProcessCallHandler,
    private val timelineService: ProcessInstanceTimelineService,
    private val historicVariableArchiver: HistoricVariableArchiver
) {

    private val taskSortableFields = setOf(
        "id",
        "processInstanceId",
        "title",
        "nodeId",
        "assignee",
        "status",
        "createdAt",
        "completedAt",
        "formId"
    )

    /* =========================
       TASK COMPLETION
     ========================= */

    @Transactional
    fun completeTask(taskId: Long, assignee: String, variables: Map<String, Any?>) {
        completeTask(taskId, assignee, emptySet(), variables)
    }

    @Transactional
    fun completeTask(taskId: Long, assignee: String, groups: Set<String>, variables: Map<String, Any?>) {
        val startTime = System.currentTimeMillis()

        val task = getActiveTaskForUpdate(taskId)
        authorizeTaskInteraction(task, assignee, groups)
        if (task.assignee == null) {
            task.assignee = assignee
        }
        if (task.assignee != assignee) {
            throw IllegalStateException("Task is assigned to another user")
        }
        val instance = getProcessInstance(task.processInstanceId)

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val currentNode = findNode(definition, task.nodeId)

        // 1️⃣ Salvar dados do formulário como TASK VARIABLES
        persistTaskVariables(task, variables)
        syncNoFormTaskVariablesToProcess(task, instance, variables)

        // 2️⃣ OUTPUT mapping → TASK → PROCESS
        applyTaskOutputs(task, currentNode, instance)

        // 3️⃣ Resolver próximos nós
        val nextNodeIds = getNextNodes(currentNode, definition, instance)

        // 4️⃣ Atualizar Task
        completeTaskEntity(task, assignee)
        timelineService.record(
            processInstanceId = task.processInstanceId,
            nodeId = task.nodeId,
            eventType = ProcessInstanceEventType.TASK_COMPLETED,
            message = "Task '${task.title ?: task.nodeId}' completed.",
            actor = assignee,
            details = "taskId=${task.id}"
        )
        metricsService.recordTaskCompleted()
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordTaskExecution(duration)

        // Publish TaskCompleted event (include provided variables)
        try {
            rabbitPublisher.publishTaskCompleted(
                mapOf(
                    "taskId" to task.id,
                    "processInstanceId" to task.processInstanceId,
                    "nodeId" to task.nodeId,
                    "assignee" to assignee,
                    "variables" to variables
                )
            )
        } catch (_: Exception) {
        }

        // 5️⃣ Atualizar instância
        advanceProcess(instance, nextNodeIds, definition)

        // 6️⃣ Continuar execução
        executeNextSteps(nextNodeIds, instance, definition)
    }

    /* =========================
       QUERY METHODS
     ========================= */

    fun getTasks(pageable: Pageable): Page<Task> {
        val startTime = System.currentTimeMillis()
        val result = taskRepository.findAll(sanitizeTaskPageable(pageable))
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordTaskQueryDuration(duration)
        return result
    }

    fun getTaskById(id: Long): Task? {
        val startTime = System.currentTimeMillis()
        val result = taskRepository.findById(id).orElse(null)
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordTaskQueryDuration(duration)
        return result
    }

    fun searchTasks(assignee: String?, status: TaskStatus?, pageable: Pageable): Page<Task> {
        val startTime = System.currentTimeMillis()
        val sanitizedPageable = sanitizeTaskPageable(pageable)
        val result = when {
            assignee != null && status != null ->
                taskRepository.findByAssigneeAndStatus(assignee, status, sanitizedPageable)

            assignee != null ->
                taskRepository.findByAssignee(assignee, sanitizedPageable)

            status != null ->
                taskRepository.findByStatus(status, sanitizedPageable)

            else -> taskRepository.findAll(sanitizedPageable)
        }
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordTaskQueryDuration(duration)
        return result
    }

    private fun sanitizeTaskPageable(pageable: Pageable): Pageable {
        val sanitizedOrders = pageable.sort
            .filter { it.property in taskSortableFields }
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

    fun getTaskResponses(pageable: Pageable): Page<TaskResponseDto> {
        return getTasks(pageable).map { toResponseDto(it) }
    }

    fun getVisibleTaskResponses(username: String, groups: Set<String>, pageable: Pageable): Page<TaskResponseDto> {
        val tasks = getTasks(pageable).content.filter { isTaskVisibleForUser(it, username, groups) }
        return PageImpl(tasks.map { toResponseDto(it) }, pageable, tasks.size.toLong())
    }

    fun getTaskResponseById(id: Long): TaskResponseDto? {
        return getTaskById(id)?.let { toResponseDto(it) }
    }

    fun getVisibleTaskResponseById(id: Long, username: String, groups: Set<String>): TaskResponseDto? {
        val task = getTaskById(id) ?: return null
        if (!isTaskVisibleForUser(task, username, groups)) {
            throw AccessDeniedException("User is not allowed to view this task")
        }
        return toResponseDto(task)
    }

    fun searchTaskResponses(assignee: String?, status: TaskStatus?, pageable: Pageable): Page<TaskResponseDto> {
        return searchTasks(assignee, status, pageable).map { toResponseDto(it) }
    }

    fun searchVisibleTaskResponses(username: String, groups: Set<String>, assignee: String?, status: TaskStatus?, pageable: Pageable): Page<TaskResponseDto> {
        val tasks = searchTasks(assignee, status, pageable).content.filter { isTaskVisibleForUser(it, username, groups) }
        return PageImpl(tasks.map { toResponseDto(it) }, pageable, tasks.size.toLong())
    }

    @Transactional
    fun claimTask(taskId: Long, username: String, groups: Set<String>): TaskResponseDto {
        val task = getActiveTaskForUpdate(taskId)
        authorizeTaskInteraction(task, username, groups)

        if (task.assignee == null) {
            task.assignee = username
            taskRepository.save(task)
            timelineService.record(
                processInstanceId = task.processInstanceId,
                nodeId = task.nodeId,
                eventType = ProcessInstanceEventType.TASK_CLAIMED,
                message = "Task '${task.title ?: task.nodeId}' claimed.",
                actor = username,
                details = "taskId=${task.id}"
            )
        } else if (task.assignee != username) {
            throw IllegalStateException("Task already claimed by another user")
        }

        return toResponseDto(taskRepository.save(task))
    }

    @Transactional
    fun unclaimTask(taskId: Long, username: String, groups: Set<String>): TaskResponseDto {
        val task = getActiveTaskForUpdate(taskId)
        authorizeTaskInteraction(task, username, groups)

        if (task.assignee != null && task.assignee != username) {
            throw IllegalStateException("Task is assigned to another user")
        }

        task.assignee = null
        return toResponseDto(taskRepository.save(task))
    }

    @Transactional
    fun saveTaskDraft(taskId: Long, username: String, groups: Set<String>, variables: Map<String, Any?>): TaskResponseDto {
        val task = getActiveTaskForUpdate(taskId)
        authorizeTaskInteraction(task, username, groups)

        if (task.assignee == null) {
            task.assignee = username
        } else if (task.assignee != username) {
            throw IllegalStateException("Task is assigned to another user")
        }

        persistTaskVariables(task, variables)
        return toResponseDto(taskRepository.save(task))
    }

    @Transactional
    fun reassignTask(taskId: Long, assignee: String?): TaskResponseDto {
        val task = getActiveTaskForUpdate(taskId)
        task.assignee = assignee?.trim()?.takeIf { it.isNotEmpty() }
        return toResponseDto(taskRepository.save(task))
    }

    /* =========================
       EXECUTION FLOW
     ========================= */

    private fun executeNextSteps(
        nodeIds: List<String>,
        instance: ProcessInstance,
        definition: JsonNode
    ) {
        nodeIds.forEach { nodeId ->
            val node = findNode(definition, nodeId)
            executeNode(instance, node, definition)
        }
    }

    private fun executeNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val nodeType = NodeType.fromString(node.get("type").asText())
        when (nodeType) {
            NodeType.UserTask -> handleUserTask(instance, node)
            NodeType.APITask -> handleAPITask(instance, node, definition)
            NodeType.ServiceTask -> handleServiceTaskNode(instance, node, definition)
            NodeType.AgentProcessCall -> handleAgentProcessCall(instance, node, definition)
            NodeType.MessageIntermediateCatchEvent -> handleMessageIntermediateCatchEvent(instance, node)
            NodeType.MessageIntermediateThrowEvent -> handleMessageIntermediateThrowEvent(instance, node, definition)
            NodeType.EndEvent -> finishProcess(instance)
                NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                    val nextNodes = getNextNodes(node, definition, instance)
                    timelineService.record(
                        processInstanceId = instance.id,
                        nodeId = node.get("id").asText(),
                        eventType = ProcessInstanceEventType.GATEWAY_EVALUATED,
                        message = "Gateway routed to ${nextNodes.joinToString(", ")}."
                    )
                    advanceProcess(instance, nextNodes, definition)
                executeNextSteps(nextNodes, instance, definition)
            }
            else -> { /* no-op for other node types */ }
        }
    }

    private fun handleUserTask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val form = resolveUserTaskForm(node)
        val config = node.get("config")
        val assigneeRaw = config?.get("assignee")?.asText()?.trim().orEmpty()
        val candidateUsers = assigneeRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()

        val candidateGroups = config?.get("candidateGroups")
            ?.asText()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()

        val directAssignee = if (candidateUsers.size == 1) candidateUsers.first() else null
        if (directAssignee != null) {
            candidateUsers.clear()
        }

        val task = taskRepository.save(
            Task(
                processInstanceId = instance.id,
                title = node.get("name").asText(),
                nodeId = node.get("id").asText(),
                assignee = directAssignee,
                candidateUsers = candidateUsers,
                candidateGroups = candidateGroups,
                formId = form?.id
            )
        )
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = task.nodeId,
            eventType = ProcessInstanceEventType.TASK_CREATED,
            message = "Task '${task.title ?: task.nodeId}' created.",
            details = "taskId=${task.id}; assignee=${task.assignee ?: ""}"
        )

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

    private fun handleAgentProcessCall(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val nodeId = node.get("id").asText()
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.AGENT_PROCESS_STARTED,
            message = "Agent process call started."
        )

        val execution = agentProcessCallHandler.execute(instance, node)

        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.AGENT_PROCESS_COMPLETED,
            message = "Agent process call completed.",
            details = "agentExecutionId=${execution.id}; status=${execution.status}"
        )

        val nextNodeIds = getNextNodes(node, definition, instance)
        advanceProcess(instance, nextNodeIds, definition)
        executeNextSteps(nextNodeIds, instance, definition)
    }

    private fun handleAPITask(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val config = node.get("properties")
            ?: throw IllegalArgumentException("APITask ${node.get("id").asText()} missing properties")

        integrationService.executeIntegration(instance, node.get("id").asText(), config)

        val nextNodeIds = getNextNodes(node, definition, instance)

        advanceProcess(instance, nextNodeIds, definition)

        executeNextSteps(nextNodeIds, instance, definition)
    }

    private fun handleServiceTaskNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val config = node.get("config")

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
                            ?: throw IllegalArgumentException("ServiceTask variable missing 'value' field for variable source")
                        val sourceVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, sourceVarName)
                        sourceVar?.value ?: throw IllegalArgumentException("Source process variable '$sourceVarName' not found")
                    }
                    else -> throw IllegalArgumentException("Invalid variable source: '$source'")
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

            // Continue to next nodes for internal service task
            val nextNodeIds = getNextNodes(node, definition, instance)
            advanceProcess(instance, nextNodeIds, definition)
            executeNextSteps(nextNodeIds, instance, definition)
        } else {
            // External service task: publish request and remain on this node until completion
            val properties = config ?: objectMapper.createObjectNode()
            try {
                rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), properties)
                timelineService.record(
                    processInstanceId = instance.id,
                    nodeId = node.get("id").asText(),
                    eventType = ProcessInstanceEventType.WORKER_REQUESTED,
                    message = "Service task request sent to worker."
                )
            } catch (_: Exception) {
            }

            instance.updatedAt = LocalDateTime.now()
            processInstanceRepository.save(instance)
        }
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

        // Create message subscription
        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            timeoutAt = null
        )
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.MESSAGE_WAITING,
            message = "Waiting for message '$messageName' with correlation key '$correlationKey'."
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
            timelineService.record(
                processInstanceId = instance.id,
                nodeId = nodeId,
                eventType = ProcessInstanceEventType.MESSAGE_THROWN,
                message = "Message '$messageName' thrown with correlation key '$correlationKey'."
            )
        } catch (ex: Exception) {
            // Log exception but continue
        }

        // Advance to next node
        val nextNodeIds = getNextNodes(node, definition, instance)

        advanceProcess(instance, nextNodeIds, definition)

        executeNextSteps(nextNodeIds, instance, definition)
    }

    /* =========================
       STATE MANAGEMENT
     ========================= */

    private fun advanceProcess(
        instance: ProcessInstance,
        nextNodeIds: List<String>,
        definition: JsonNode
    ) {
        instance.currentNode = nextNodeIds
        // Resolve and append meaningful nodes to history (skip gateways, include resolved targets and end events)
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
                NodeType.AgentProcessCall,
                NodeType.APITask,
                NodeType.MessageEvent,
                NodeType.MessageIntermediateCatchEvent,
                NodeType.MessageIntermediateThrowEvent,
                NodeType.ScriptTask,
                NodeType.EndEvent -> appendable.add(id)
                else -> { /* ignore other node types */ }
            }
        }

        nextNodeIds.forEach { resolveAndCollect(it) }

        // Avoid consecutive duplicates when appending
        appendable.forEach { id ->
            if (instance.nodeHistory.lastOrNull() != id) {
                instance.nodeHistory = instance.nodeHistory + id
                timelineService.record(
                    processInstanceId = instance.id,
                    nodeId = id,
                    eventType = ProcessInstanceEventType.NODE_ENTERED,
                    message = "Entered node '$id'."
                )
            }
        }
        instance.updatedAt = LocalDateTime.now()

        if (nextNodeIds.isEmpty() || nextNodeIds.all { isEndEvent(it, definition) }) {
            instance.status = ProcessStatus.COMPLETED
            timelineService.record(
                processInstanceId = instance.id,
                eventType = ProcessInstanceEventType.PROCESS_COMPLETED,
                message = "Process instance completed."
            )
        }

        processInstanceRepository.save(instance)
        if (instance.status == ProcessStatus.COMPLETED) {
            historicVariableArchiver.archiveProcessInstanceVariables(instance.id)
        }
    }

    private fun completeTaskEntity(task: Task, assignee: String) {
        task.assignee = assignee
        task.status = TaskStatus.COMPLETED
        task.completedAt = LocalDateTime.now()
        taskRepository.save(task)
        historicVariableArchiver.archiveTaskVariables(task)
    }

    private fun toResponseDto(task: Task): TaskResponseDto {
        val taskVariables = taskVariableRepository.findByTaskId(task.id)
        val historicTaskVariables = if (taskVariables.isEmpty() && task.status == TaskStatus.COMPLETED) {
            historicTaskVariableRepository.findByTaskId(task.id)
        } else {
            emptyList()
        }
        val variables = if (taskVariables.isNotEmpty()) {
            taskVariables.associate { it.name to objectMapper.convertValue(it.value, Any::class.java) }
        } else {
            historicTaskVariables.associate { it.name to objectMapper.convertValue(it.value, Any::class.java) }
        }
        val formId = task.formId?.let { formService.getById(it)?.formId }

        return TaskResponseDto(
            id = task.id,
            title = task.title,
            name = task.title ?: "Task ${task.id}",
            description = task.title,
            processInstanceId = task.processInstanceId,
            nodeId = task.nodeId,
            assignee = task.assignee,
            candidateUsers = task.candidateUsers,
            candidateGroups = task.candidateGroups,
            status = task.status,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            formDbId = task.formId,
            formId = formId,
            variables = variables
        )
    }

    private fun resolveUserTaskForm(node: JsonNode) =
        node.get("config")?.get("formId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredFormRef ->
            formService.getLatestVersionByFormId(configuredFormRef)
                ?: configuredFormRef.toLongOrNull()?.let(formService::getById)
                ?: formService.getLatestVersionByName(configuredFormRef)
        } ?: formService.getLatestVersionByName(node.get("id").asText())

    private fun finishProcess(instance: ProcessInstance) {
        instance.status = ProcessStatus.COMPLETED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
        historicVariableArchiver.archiveProcessInstanceVariables(instance.id)
    }

    /* =========================
       VARIABLE MANAGEMENT
     ========================= */

    private fun persistTaskVariables(task: Task, variables: Map<String, Any?>) {
        variables.forEach { (key, value) ->
            val valueNode = objectMapper.valueToTree<JsonNode>(value)
            val existingVariables = taskVariableRepository.findAllByTaskIdAndNameOrderByIdDesc(task.id, key)
            val latest = existingVariables.firstOrNull()

            if (latest != null) {
                latest.value = valueNode
                taskVariableRepository.save(latest)
                existingVariables.drop(1).forEach { taskVariableRepository.delete(it) }
            } else {
                taskVariableRepository.save(
                    TaskVariable(
                        taskId = task.id,
                        processInstanceId = task.processInstanceId,
                        name = key,
                        value = valueNode
                    )
                )
            }
        }
    }

    private fun syncNoFormTaskVariablesToProcess(
        task: Task,
        instance: ProcessInstance,
        variables: Map<String, Any?>
    ) {
        if (task.formId != null) return

        variables.forEach { (key, value) ->
            val valueNode = objectMapper.valueToTree<JsonNode>(value)
            val existingVariable = processVariableRepository.findByProcessInstanceIdAndName(instance.id, key)

            if (existingVariable != null) {
                existingVariable.value = valueNode
                processVariableRepository.save(existingVariable)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = key,
                        value = valueNode
                    )
                )
            }
        }
    }

    private fun applyTaskOutputs(
        task: Task,
        node: JsonNode,
        instance: ProcessInstance
    ) {
        val outputs = node.get("config")?.get("outputs") ?: return

        outputs.forEach { output ->
            val target = output.get("target")?.asText()
                ?: throw IllegalArgumentException("Output missing 'target'")

            if (target != "variable") return@forEach

            val processVarName = output.get("value")?.asText()
                ?: throw IllegalArgumentException("Output missing 'value' (process variable name)")

            val sourceName = output.get("sourceName")?.asText()

            val finalValue: JsonNode = if (!sourceName.isNullOrBlank()) {
                val taskVar = taskVariableRepository
                    .findAllByTaskIdAndNameOrderByIdDesc(task.id, sourceName)
                    .firstOrNull()
                taskVar?.value ?: NullNode.instance
            } else {
                parseStaticValue(output.get("value"))
            }

            val existingVar =
                processVariableRepository.findByProcessInstanceIdAndName(instance.id, processVarName)

            if (existingVar != null) {
                existingVar.value = finalValue
                processVariableRepository.save(existingVar)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = processVarName,
                        value = finalValue
                    )
                )
            }
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
                    processInstanceId = task.processInstanceId,
                    name = targetName,
                    value = value
                )
            )
        }
    }

    /* =========================
       HELPERS
     ========================= */

    private fun getActiveTask(taskId: Long): Task {
        val task = taskRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task not found") }

        if (task.status == TaskStatus.COMPLETED) {
            throw IllegalStateException("Task already completed")
        }

        return task
    }

    private fun getActiveTaskForUpdate(taskId: Long): Task {
        val task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow { IllegalArgumentException("Task not found") }

        if (task.status == TaskStatus.COMPLETED) {
            throw IllegalStateException("Task already completed")
        }

        return task
    }

    private fun isTaskVisibleForUser(task: Task, username: String, groups: Set<String>): Boolean {
        if (task.assignee != null) return task.assignee == username
        val userEligible = task.candidateUsers.isEmpty() || task.candidateUsers.contains(username)
        val groupEligible = task.candidateGroups.isEmpty() || task.candidateGroups.any { groups.contains(it) }
        return userEligible && groupEligible
    }

    private fun authorizeTaskInteraction(task: Task, username: String, groups: Set<String>) {
        if (!isTaskVisibleForUser(task, username, groups)) {
            throw AccessDeniedException("User is not allowed to interact with this task")
        }
    }

    private fun getProcessInstance(instanceId: Long): ProcessInstance =
        processInstanceRepository.findByIdForUpdate(instanceId)
            ?: throw IllegalArgumentException("Process instance not found")

    private fun parseDefinition(definitionJson: String): JsonNode =
        objectMapper.readTree(definitionJson)

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")

    

    private fun getNextNodes(node: JsonNode, definition: JsonNode, instance: ProcessInstance): List<String> {
        return gatewayService.getNextNodes(node, definition, instance)
    }

    private fun evaluateCondition(condition: String, instance: ProcessInstance): Boolean {
        return gatewayService.evaluateCondition(condition, instance)
    }

    private fun isEndEvent(nodeId: String, definition: JsonNode): Boolean {
        val node = findNode(definition, nodeId)
        return NodeType.fromString(node.get("type").asText()) == NodeType.EndEvent
    }

    private fun parseStaticValue(valueNode: JsonNode): JsonNode =
        if (valueNode.isTextual) objectMapper.readTree(valueNode.asText()) else valueNode
}
