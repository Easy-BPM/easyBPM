package com.easy.bpm.service

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
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
    private val integrationService: IntegrationService,
    private val formService: FormService,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun completeTask(taskId: Long, assignee: String, variables: Map<String, Any>) {

        val task = processTaskRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task not found") }

        if (task.status == TaskStatus.COMPLETED) {
            throw IllegalStateException("Task already completed")
        }

        val instance = processInstanceRepository.findById(task.processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        val definitionJson = instance.processDefinition.definitionJson
        val jsonNode = objectMapper.readTree(definitionJson)
        val nodes = jsonNode.get("nodes")

        val currentNode = nodes.find { it.get("id").asText() == task.nodeId }
            ?: throw IllegalStateException("Node '${task.nodeId}' not found")

        // 1️⃣ Salvar dados do formulário como TASK VARIABLES
        variables.forEach { (key, value) ->
            val taskVar = TaskVariable(
                taskId = task.id,
                name = key,
                value = objectMapper.valueToTree(value)
            )
            taskVariableRepository.save(taskVar)
        }

        // 2️⃣ Aplicar OUTPUT mapping → TASK → PROCESS
        applyTaskOutputs(task, currentNode, instance)

        // 3️⃣ Limpar TaskVariables - NOT ACTIVE
        //taskVariableRepository.deleteByTaskId(task.id)

        // 4️⃣ Resolver próximos nós
        val nextNodeIds = currentNode.get("next")?.map { it.asText() } ?: emptyList()

        // 5️⃣ Atualizar Task
        task.assignee = assignee
        task.status = TaskStatus.COMPLETED
        task.completedAt = LocalDateTime.now()
        processTaskRepository.save(task)

        // 6️⃣ Atualizar Instância
        instance.currentNode = nextNodeIds
        instance.updatedAt = LocalDateTime.now()

        if (nextNodeIds.isEmpty() || nextNodeIds.all { isEndEvent(it, nodes) }) {
            instance.status = ProcessStatus.COMPLETED
        }

        processInstanceRepository.save(instance)

        // 7️⃣ Continuar execução
        createUserTasksIfAny(nextNodeIds, instance, definitionJson)
        handleIntegrationTasksIfAny(nextNodeIds, instance, definitionJson)
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

        nodeIds.forEach { nodeId ->
            val node = nodes.find { it.get("id").asText() == nodeId }

            if (node?.get("type")?.asText() == "UserTask") {
                val form = formService.getLatestVersionByName(nodeId)

                val task = processTaskRepository.save(
                    Task(
                        processInstanceId = instance.id,
                        nodeId = nodeId,
                        formId = form?.id
                    )
                )

                // 🔥 aplica input mapping aqui
                applyTaskInputs(task, node, instance)
            }
        }
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

    private fun serializeVariableValue(value: Any): String {
        return objectMapper.writeValueAsString(value)
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

    private fun applyTaskOutputs(
        task: Task,
        node: JsonNode,
        instance: ProcessInstance
    ) {
        val outputs = node.get("config")?.get("outputs") ?: return

        outputs.forEach { output ->
            val sourceName = output.get("sourceName")?.asText()
            val target = output.get("target")?.asText()
                ?: throw IllegalArgumentException("Output missing 'target'")

            if (target != "variable") return@forEach

            val processVarName = output.get("value")?.asText()
                ?: throw IllegalArgumentException("Output missing 'value' (process variable name)")

            val finalValue: JsonNode = when {
                !sourceName.isNullOrBlank() -> {
                    val taskVar = taskVariableRepository
                        .findByTaskIdAndName(task.id, sourceName)
                        ?: throw IllegalArgumentException(
                            "Task variable '$sourceName' not found for task ${task.id}"
                        )
                    taskVar.value
                }

                else -> {
                    parseStaticValue(output.get("value"))
                }
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

    private fun parseStaticValue(valueNode: JsonNode): JsonNode {
        return if (valueNode.isTextual) {
            objectMapper.readTree(valueNode.asText())
        } else {
            valueNode
        }
    }
    private fun applyTaskInputs(
        task: Task,
        node: JsonNode,
        instance: ProcessInstance
    ) {
        val config = node.get("config") ?: return
        val inputs = config.get("inputs") ?: return

        inputs.forEach { input ->
            val targetName = input.get("targetName").asText()
            val source = input.get("source").asText()
            val valueNode = input.get("value")

            val value: JsonNode = when (source) {
                "variable" -> {
                    val varName = valueNode.asText()
                    val processVar = processVariableRepository
                        .findByProcessInstanceIdAndName(instance.id, varName)
                        ?: throw IllegalArgumentException("Process variable '$varName' not found")
                    processVar.value
                }

                "static" -> valueNode

                else -> throw IllegalArgumentException("Invalid input source '$source'")
            }

            val taskVar = TaskVariable(
                taskId = task.id,
                name = targetName,
                value = value
            )

            taskVariableRepository.save(taskVar)
        }
    }

}
