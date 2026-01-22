package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

@Service
class ProcessService (
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val integrationService: IntegrationService,
    private val taskRepository: TaskRepository,
    private val objectMapper: ObjectMapper
){

    @Transactional
    fun deployProcess(definitionJson: JsonNode): ProcessDefinition {

        val jsonNode = definitionJson.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Root JSON must be an object")

        val processId = jsonNode.get("processId")?.asText()
            ?: throw IllegalArgumentException("Missing 'processId'")

        val nodes = jsonNode.get("nodes")
            ?: throw IllegalArgumentException("Missing 'nodes' in process definition")
        if (!nodes.isArray) throw IllegalArgumentException("'nodes' must be an array")

        val flows = jsonNode.get("flows")
            ?: throw IllegalArgumentException("Missing 'flows' in process definition")
        if (!flows.isArray) throw IllegalArgumentException("'flows' must be an array")

        val variables = jsonNode.get("variables")
        if (variables != null && !variables.isArray) {
            throw IllegalArgumentException("'variables' must be an array")
        }

        val validTypes = setOf(
            "StartEvent", "EndEvent", "UserTask", "Integration",
            "InclusiveGateway", "ExclusiveGateway", "ParallelGateway",
            "ServiceTask", "ScriptTask", "TimerEvent", "MessageEvent", "CallActivity"
        )

        val nodeIds = mutableSetOf<String>()

        // 2. Validar variáveis
        variables?.forEach { variable ->
            val name = variable.get("name")?.asText()
                ?: throw IllegalArgumentException("Every variable must have 'name'")
            val type = variable.get("type")?.asText()
                ?: throw IllegalArgumentException("Variable '$name' must have 'type'")

            if (type !in setOf("string", "number", "boolean", "object")) {
                throw IllegalArgumentException("Invalid variable type '$type' at '$name'")
            }
        }

        // 3. Validar nós
        for (node in nodes) {
            val id = node.get("id")?.asText()
                ?: throw IllegalArgumentException("Every node must have an 'id'")

            val type = node.get("type")?.asText()
                ?: throw IllegalArgumentException("Every node must have a 'type'")

            val next = node.get("next")
                ?: throw IllegalArgumentException("Node $id must have 'next'")

            if (!validTypes.contains(type)) {
                throw IllegalArgumentException("Invalid node type '$type' at node '$id'")
            }

            if (!next.isArray) {
                throw IllegalArgumentException("Node $id: 'next' must be an array")
            }

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }

            // Validação do config (inputs / outputs)
            if (node.has("config")) {
                val config = node.get("config")

                if (config.has("inputs") && !config.get("inputs").isArray) {
                    throw IllegalArgumentException("Node $id: 'config.inputs' must be an array")
                }

                if (config.has("outputs") && !config.get("outputs").isArray) {
                    throw IllegalArgumentException("Node $id: 'config.outputs' must be an array")
                }

                config.get("inputs")?.forEach { input ->
                    require(input.hasNonNull("targetName")) { "Node $id: input missing 'targetName'" }
                    require(input.hasNonNull("type")) { "Node $id: input missing 'type'" }
                    require(input.hasNonNull("source")) { "Node $id: input missing 'source'" }
                    require(input.has("value")) { "Node $id: input missing 'value'" }
                }

                config.get("outputs")?.forEach { output ->
                    require(output.hasNonNull("sourceName")) { "Node $id: output missing 'sourceName'" }
                    require(output.hasNonNull("type")) { "Node $id: output missing 'type'" }
                    require(output.hasNonNull("target")) { "Node $id: output missing 'target'" }
                    require(output.has("value")) { "Node $id: output missing 'value'" }
                }
            }
        }

        // 4. Validar referências em 'next'
        val referencedNext = nodes.flatMap {
            it.get("next").map { n -> n.asText() }
        }.toSet()

        val undefinedNext = referencedNext - nodeIds
        if (undefinedNext.isNotEmpty()) {
            throw IllegalArgumentException("Found 'next' references to undefined nodes: $undefinedNext")
        }

        // 5. Validar flows
        for (flow in flows) {
            val from = flow.get("from")?.asText()
                ?: throw IllegalArgumentException("Flow missing 'from'")

            val to = flow.get("to")?.asText()
                ?: throw IllegalArgumentException("Flow missing 'to'")

            if (from !in nodeIds) {
                throw IllegalArgumentException("Flow references undefined 'from' node: $from")
            }

            if (to !in nodeIds) {
                throw IllegalArgumentException("Flow references undefined 'to' node: $to")
            }
        }

        // 6. Versionamento
        val latestVersion =
            processDefinitionRepository.findTopByNameOrderByVersionDesc(processId)

        val nextVersion = (latestVersion?.version ?: 0) + 1

        val process = ProcessDefinition(
            name = processId,
            definitionJson = jsonNode.toString(),
            version = nextVersion
        )

        return processDefinitionRepository.save(process)
    }


    @Transactional
    fun startProcessInstance(processDefinitionId: Long): ProcessInstance {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        val startNodes = getStartNextNodes(definition.definitionJson)

        val instance = ProcessInstance(
            processDefinition = definition,
            status = ProcessStatus.ACTIVE,
            currentNode = startNodes
        )

        processInstanceRepository.save(instance)

        // 🔥 Inicializa variáveis aqui
        initializeProcessVariables(instance, definition.definitionJson)

        createUserTasksIfAny(startNodes, instance, definition.definitionJson)
        handleIntegrationTasksIfAny(startNodes, instance, definition.definitionJson)

        return instance
    }

    private fun getStartNextNodes(definitionJson: String): List<String> {
        val jsonNode: JsonNode = objectMapper.readTree(definitionJson)
        val nodes = jsonNode.get("nodes")

        // Find the StartEvent node
        val startNode = nodes.find { it.get("type").asText() == "StartEvent" }
                ?: throw IllegalArgumentException("No StartEvent found in process definition")

        // Get its 'next' array of node ids
        val nextNodes = startNode.get("next")

        // Collect all node ids after the start event
        val startNextNodes = mutableListOf<String>()
        for (nodeIdNode in nextNodes) {
            startNextNodes.add(nodeIdNode.asText())
        }

        if (startNextNodes.isEmpty()) {
            throw IllegalArgumentException("No nodes found after StartEvent")
        }

        return startNextNodes
    }

    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> {
        return processInstanceRepository.findAll(pageable)
    }

    private fun createUserTasksIfAny(
        nodeIds: List<String>,
        instance: ProcessInstance,
        definitionJson: String
    ) {
        val nodes = objectMapper.readTree(definitionJson).get("nodes")
        val tasks = nodeIds.mapNotNull { nodeId ->
            val node = nodes.find { it.get("id").asText() == nodeId }
            if (node?.get("type")?.asText() == "UserTask") {
                Task(processInstanceId = instance.id, nodeId = nodeId)
            } else null
        }

        if (tasks.isNotEmpty()) {
            // injete o repositório no construtor se ainda não tiver
            taskRepository.saveAll(tasks)
        }
    }

    fun getLatestProcessDefinitions(pageable: Pageable): Page<ProcessDefinition> {
        return processDefinitionRepository.findLatestVersionProcesses(pageable)
    }

    private fun handleIntegrationTasksIfAny(
        nodeIds: List<String>,
        instance: ProcessInstance,
        definitionJson: String
    ) {
        val nodes = objectMapper.readTree(definitionJson).get("nodes")

        nodeIds.forEach { nodeId ->
            val node = nodes.find { it.get("id").asText() == nodeId }

            if (node?.get("type")?.asText() == "ServiceTask") {
                val config = node.get("properties") ?: throw IllegalArgumentException("ServiceTask $nodeId missing properties")

                val outputs = integrationService.executeIntegration(instance, nodeId, config)

                // avançar no grafo
                val nextNodeIds = node.get("next")?.map { it.asText() } ?: emptyList()
                instance.currentNode = nextNodeIds
                instance.updatedAt = LocalDateTime.now()

                processInstanceRepository.save(instance)

                // continuar fluxo
                handleIntegrationTasksIfAny(nextNodeIds, instance, definitionJson)
                createUserTasksIfAny(nextNodeIds, instance, definitionJson)
            }
        }
    }

    private fun initializeProcessVariables(
        instance: ProcessInstance,
        definitionJson: String
    ) {
        val jsonNode = objectMapper.readTree(definitionJson)
        val variablesNode = jsonNode.get("variables") ?: return

        val variables = variablesNode.map { variableNode ->
            val name = variableNode.get("name").asText()
            val initialValueNode = variableNode.get("initialValue") ?: objectMapper.nullNode()

            ProcessVariable(
                processInstanceId = instance.id,
                name = name,
                value = initialValueNode
            )
        }

        if (variables.isNotEmpty()) {
            processVariableRepository.saveAll(variables)
        }
    }



}