package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val metricsService: MetricsService
) {

    /* =========================
       DEPLOY
     ========================= */

    @Transactional
    fun deployProcess(definitionJson: JsonNode): ProcessDefinition {
        val json = validateAndParseDefinition(definitionJson)

        val processId = json.get("processId").asText()

        val latestVersion =
            processDefinitionRepository.findTopByNameOrderByVersionDesc(processId)

        val nextVersion = (latestVersion?.version ?: 0) + 1

        return processDefinitionRepository.save(
            ProcessDefinition(
                name = processId,
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
        val startTime = System.currentTimeMillis()
        
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

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
        processInstanceRepository.findAll(pageable)

    fun getProcessInstanceById(id: Long): ProcessInstance? =
        processInstanceRepository.findById(id).orElse(null)

    fun getLatestProcessDefinitions(pageable: Pageable): Page<ProcessDefinition> =
        processDefinitionRepository.findLatestVersionProcesses(pageable)

    /* =========================
       EXECUTION ENGINE CORE
     ========================= */

    private fun executeNodes(
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
        val startTime = System.currentTimeMillis()
        val nodeType = NodeType.fromString(node.get("type").asText())
        
        when (nodeType) {
            NodeType.UserTask -> handleUserTask(instance, node)
            NodeType.APITask -> handleAPITask(instance, node)
            NodeType.ServiceTask -> handleServiceTaskNode(instance, node, definition)
            NodeType.MessageEvent -> handleMessageEvent(instance, node)
            NodeType.MessageIntermediateCatchEvent -> handleMessageIntermediateCatchEvent(instance, node)
            NodeType.MessageIntermediateThrowEvent -> handleMessageIntermediateThrowEvent(instance, node, definition)
            NodeType.EndEvent -> finishProcess(instance)
            NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                val nextNodes = getNextNodes(node, definition, instance)
                advanceProcess(instance, nextNodes, definition)
                executeNodes(nextNodes, instance, definition)
            }
            else -> { /* no-op for other node types */ }
        }
        
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordNodeExecution(duration, nodeType.toString())
    }

    private fun handleUserTask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val form = formService.getLatestVersionByName(node.get("id").asText())
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
                    "formId" to task.formId
                )
            )
        } catch (_: Exception) {
        }

        applyTaskInputs(task, node, instance)
    }

    private fun handleAPITask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val config = node.get("properties")
            ?: throw IllegalArgumentException("APITask ${node.get("id").asText()} missing properties")

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
                node.get("properties")
                    ?: throw IllegalArgumentException("ServiceTask $id missing 'properties'")
            }

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }
        }

        return json
    }
}
