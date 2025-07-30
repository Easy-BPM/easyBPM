package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime


@Service
class TaskService(
    private val processTaskRepository: TaskRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun completeTask(taskId: Long, assignee: String, variables: Map<String, String>) {
        val task = processTaskRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task not found") }

        if (task.status == TaskStatus.COMPLETED) {
            throw IllegalStateException("Task already completed")
        }

        // ⚠️ Recuperar a instancia pelo ID armazenado na task
        val instance = processInstanceRepository.findById(task.processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        // Salva as variáveis como variáveis de processo
        val processVars = variables.map { (key, value) ->
            ProcessVariable(processInstanceId = instance.id, name = key, value = value)
        }
        processVariableRepository.saveAll(processVars)

        // Remove as variáveis da tarefa (se existirem)
        taskVariableRepository.deleteByTaskId(taskId)

        val definitionJson = instance.processDefinition.definitionJson
        val jsonNode = objectMapper.readTree(definitionJson)
        val nodes = jsonNode.get("nodes")

        val currentNode = nodes.find { it.get("id").asText() == task.nodeId }
        val nextNodeIds = currentNode?.get("next")?.map { it.asText() } ?: emptyList()

        // ✅ Atualiza e salva a tarefa
        task.assignee = assignee
        task.status = TaskStatus.COMPLETED
        task.completedAt = LocalDateTime.now()
        processTaskRepository.save(task)

        // ✅ Atualiza a instância de processo
        instance.currentNode = nextNodeIds
        instance.updatedAt = LocalDateTime.now()

        if (nextNodeIds.isEmpty() || nextNodeIds.all { isEndEvent(it, nodes) }) {
            instance.status = ProcessStatus.COMPLETED
        }

        processInstanceRepository.save(instance)

        // ✅ Cria novas tarefas se necessário
        createUserTasksIfAny(nextNodeIds, instance, definitionJson)
    }

    private fun isEndEvent(nodeId: String, nodes: JsonNode): Boolean {
        val node = nodes.find { it.get("id").asText() == nodeId }
        return node?.get("type")?.asText() == "EndEvent"
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

        processTaskRepository.saveAll(tasks)
    }

    fun getTasks(pageable: Pageable): Page<Task> {
        return processTaskRepository.findAll(pageable)
    }

    fun getTaskById(id: Long): Task? {
        return processTaskRepository.findById(id).orElse(null)
    }

    fun searchTasks(assignee: String?, status: TaskStatus?, pageable: Pageable): Page<Task> {
        return when {
            assignee != null && status != null ->
                processTaskRepository.findByAssigneeAndStatus(assignee, status, pageable)
            assignee != null ->
                processTaskRepository.findByAssignee(assignee, pageable)
            status != null ->
                processTaskRepository.findByStatus(status, pageable)
            else ->
                processTaskRepository.findAll(pageable)
        }
    }
}
