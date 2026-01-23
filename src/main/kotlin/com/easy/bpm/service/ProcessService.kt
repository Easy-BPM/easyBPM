package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
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
    private val objectMapper: ObjectMapper
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

        val startNodes = getStartNodes(json)

        val instance = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = startNodes
            )
        )

        initializeProcessVariables(instance, json)

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
        when (node.get("type").asText()) {
            "UserTask" -> handleUserTask(instance, node)
            "ServiceTask" -> handleServiceTask(instance, node, definition)
            "EndEvent" -> finishProcess(instance)
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

        applyTaskInputs(task, node, instance)
    }

    private fun handleServiceTask(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val config = node.get("properties")
            ?: throw IllegalArgumentException("ServiceTask missing properties")

        integrationService.executeIntegration(
            instance,
            node.get("id").asText(),
            config
        )

        val nextNodes = getNextNodes(node)

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

    private fun getNextNodes(node: JsonNode): List<String> =
        node.get("next")?.map { it.asText() } ?: emptyList()

    private fun getStartNodes(definition: JsonNode): List<String> {
        val start = definition.get("nodes")
            .find { it.get("type").asText() == "StartEvent" }
            ?: throw IllegalArgumentException("StartEvent not found")

        return getNextNodes(start).also {
            if (it.isEmpty()) {
                throw IllegalArgumentException("StartEvent has no outgoing flow")
            }
        }
    }

    private fun parseStaticValue(valueNode: JsonNode?): JsonNode {
        if (valueNode == null || valueNode.isNull) return objectMapper.nullNode()

        return if (valueNode.isTextual) {
            objectMapper.readTree(valueNode.asText())
        } else {
            valueNode
        }
    }

    /* =========================
       DEPLOY VALIDATION
     ========================= */

    private fun validateAndParseDefinition(definitionJson: JsonNode): JsonNode {
        val json = definitionJson.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Root JSON must be an object")

        val processId = json.get("processId")?.asText()
            ?: throw IllegalArgumentException("Missing 'processId'")

        val nodes = json.get("nodes")
            ?: throw IllegalArgumentException("Missing 'nodes'")
        require(nodes.isArray) { "'nodes' must be an array" }

        val flows = json.get("flows")
            ?: throw IllegalArgumentException("Missing 'flows'")
        require(flows.isArray) { "'flows' must be an array" }

        val validTypes = setOf(
            "StartEvent", "EndEvent", "UserTask", "Integration",
            "InclusiveGateway", "ExclusiveGateway", "ParallelGateway",
            "ServiceTask", "ScriptTask", "TimerEvent", "MessageEvent", "CallActivity"
        )

        val nodeIds = mutableSetOf<String>()

        nodes.forEach { node ->
            val id = node.get("id")?.asText()
                ?: throw IllegalArgumentException("Node missing 'id'")

            val type = node.get("type")?.asText()
                ?: throw IllegalArgumentException("Node $id missing 'type'")

            if (type !in validTypes) {
                throw IllegalArgumentException("Invalid node type '$type' at '$id'")
            }

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }
        }

        return json
    }
}
