package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessVariableManager(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun assignProcessVariables(processInstanceId: Long, variables: Map<String, Any?>): List<ProcessVariable> {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        variables.forEach { (name, value) ->
            val jsonValue = if (value == null) objectMapper.nullNode() else objectMapper.valueToTree(value)
            upsertProcessVariable(processInstanceId, name, jsonValue)
        }

        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        return processVariableRepository.findByProcessInstanceId(processInstanceId)
    }

    fun initializeProcessVariables(instance: ProcessInstance, definition: JsonNode) {
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

    fun applyTaskInputs(task: Task, node: JsonNode, instance: ProcessInstance) {
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

    fun saveMessageVariables(instance: ProcessInstance, variables: Map<String, Any>?) {
        variables?.forEach { (name, rawValue) ->
            val value = objectMapper.convertValue(rawValue, JsonNode::class.java)
            upsertProcessVariable(instance.id, name, value)
        }
    }

    fun upsertProcessVariable(processInstanceId: Long, name: String, value: JsonNode) {
        val existing = processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, name)

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

    fun evaluateCorrelationKey(template: String, instance: ProcessInstance): String {
        val regex = Regex("\\$\\{([^}]+)\\}")

        return regex.replace(template) { match ->
            val varName = match.groupValues[1]
            val processVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
            when {
                processVar == null || processVar.value.isNull -> varName
                processVar.value.isTextual -> processVar.value.asText()
                processVar.value.isNumber -> processVar.value.toString()
                else -> processVar.value.toString()
            }
        }
    }

    fun extractValueByPath(node: JsonNode, path: String): JsonNode {
        if (path.isBlank()) return node

        val parts = path.split(".")
        var current: JsonNode? = node

        for (part in parts) {
            if (current == null) break
            current = current.get(part)
        }

        return current ?: objectMapper.nullNode()
    }

    fun parseStaticValue(valueNode: JsonNode?): JsonNode {
        if (valueNode == null || valueNode.isNull) return objectMapper.nullNode()

        if (!valueNode.isTextual) return valueNode

        val text = valueNode.asText()
        val trimmed = text.trim()

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
}
