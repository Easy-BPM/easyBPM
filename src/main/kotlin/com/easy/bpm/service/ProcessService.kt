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
    private val gatewayService: GatewayService
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

        initializeProcessVariables(instance, json)

        val startNodes = getStartNodes(instance, json)

        instance.currentNode = startNodes
        processInstanceRepository.save(instance)

        executeNodes(startNodes, instance, json)

        return instance
    }

    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> =
        processInstanceRepository.findAll(pageable)

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
        val nodeType = NodeType.fromString(node.get("type").asText())
        when (nodeType) {
            NodeType.UserTask -> handleUserTask(instance, node)
            NodeType.ServiceTask -> handleServiceTask(instance, node)
            NodeType.EndEvent -> finishProcess(instance)
            NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                val nextNodes = getNextNodes(node, definition, instance)
                advanceProcess(instance, nextNodes)
                executeNodes(nextNodes, instance, definition)
            }
            else -> { /* no-op for other node types */ }
        }
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

    private fun handleServiceTask(
        instance: ProcessInstance,
        node: JsonNode
    ) {
        val config = node.get("properties")
            ?: throw IllegalArgumentException("ServiceTask ${node.get("id").asText()} missing properties")

        // Publish a service task request to RabbitMQ; worker will send a completion event.
        rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), config)

        // Persist instance updated timestamp; instance remains on this node until completion.
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    @Transactional
    fun handleServiceTaskCompleted(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        // Save outputs as process variables
        outputs.forEach { (k, v) ->
            val newVar = ProcessVariable(
                processInstanceId = instance.id,
                name = k,
                value = objectMapper.valueToTree(v)
            )
            processVariableRepository.save(newVar)
        }

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val nextNodes = getNextNodes(node, definition, instance)

        advanceProcess(instance, nextNodes)

        executeNodes(nextNodes, instance, definition)
    }

    private fun finishProcess(instance: ProcessInstance) {
        instance.status = ProcessStatus.COMPLETED
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }

    private fun advanceProcess(
        instance: ProcessInstance,
        nextNodes: List<String>
    ) {
        instance.currentNode = nextNodes
        instance.updatedAt = LocalDateTime.now()

        if (nextNodes.isEmpty()) {
            instance.status = ProcessStatus.COMPLETED
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

            try {
                NodeType.fromString(typeText)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid node type '$typeText' at '$id'")
            }

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }
        }

        return json
    }
}
