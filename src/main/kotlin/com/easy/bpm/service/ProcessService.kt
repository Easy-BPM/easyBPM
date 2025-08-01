package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Service
class ProcessService (
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val taskRepository: TaskRepository,
    private val objectMapper: ObjectMapper
){

    @Transactional
    fun deployProcess(name: String, definitionJson: String): ProcessDefinition {
        // 1. Parse JSON
        val jsonNode = try {
            objectMapper.readTree(definitionJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON format")
        }

        val nodes = jsonNode.get("nodes") ?: throw IllegalArgumentException("Missing 'nodes' in process definition")
        if (!nodes.isArray) throw IllegalArgumentException("'nodes' must be an array")

        val validTypes = setOf(
            "StartEvent", "EndEvent", "UserTask", "Integration",
            "InclusiveGateway", "ExclusiveGateway", "ParallelGateway",
            "ServiceTask", "ScriptTask", "TimerEvent", "MessageEvent", "CallActivity"
        )

        val nodeIds = mutableSetOf<String>()

        for (node in nodes) {
            val id = node.get("id")?.asText()
                ?: throw IllegalArgumentException("Every node must have an 'id'")
            val type = node.get("type")?.asText()
                ?: throw IllegalArgumentException("Every node must have a 'type'")
            val next = node.get("next")
                ?: throw IllegalArgumentException("Node $id must have a 'next' array")

            if (!validTypes.contains(type)) {
                throw IllegalArgumentException("Invalid node type '$type' at node '$id'")
            }

            if (!next.isArray) throw IllegalArgumentException("Node $id: 'next' must be an array")
            if (!nodeIds.add(id)) throw IllegalArgumentException("Duplicate node id '$id'")

            // Validar variáveis de tarefa (se for UserTask)
            if (type == "UserTask") {
                if (node.has("taskVariables")) {
                    val taskVars = node.get("taskVariables")
                    if (!taskVars.isArray || taskVars.any { !it.isTextual }) {
                        throw IllegalArgumentException("Node $id: 'taskVariables' must be an array of strings")
                    }
                }
            }
        }

        // 2. Validar referências de 'next'
        val referencedIds = nodes.flatMap { it.get("next").map { n -> n.asText() } }.toSet()
        val undefinedRefs = referencedIds - nodeIds
        if (undefinedRefs.isNotEmpty()) {
            throw IllegalArgumentException("Found 'next' references to undefined nodes: $undefinedRefs")
        }

        // 3. Persistir processo com versão
        val latestVersion = processDefinitionRepository.findTopByNameOrderByVersionDesc(name)
        val nextVersion = if (latestVersion != null) latestVersion.version + 1 else 1

        val process = ProcessDefinition(
            name = name,
            definitionJson = definitionJson,
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
        createUserTasksIfAny(startNodes, instance, definition.definitionJson)
        return instance;
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



}