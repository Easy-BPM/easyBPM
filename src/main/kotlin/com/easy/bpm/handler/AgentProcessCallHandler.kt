package com.easy.bpm.handler

import com.easy.bpm.model.agent.AgentProcessExecution
import com.easy.bpm.model.agent.AgentProcessExecutionStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.easy.bpm.repository.agent.AgentProcessExecutionRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AgentProcessCallHandler(
    private val definitionRepository: AgentProcessDefinitionRepository,
    private val executionRepository: AgentProcessExecutionRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun execute(instance: ProcessInstance, node: JsonNode): AgentProcessExecution {
        val nodeId = node.get("id").asText()
        val config = node.get("config")
            ?: throw IllegalArgumentException("AgentProcessCall $nodeId missing 'config'")
        val agentProcessKey = config.get("agentProcessKey")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: config.get("processKey")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("AgentProcessCall $nodeId missing 'agentProcessKey'")

        val definition = definitionRepository.findTopByKeyOrderByVersionDesc(agentProcessKey)
            ?: throw IllegalArgumentException("Agent process '$agentProcessKey' not found")

        val inputPayload = resolveInputPayload(instance.id, config)
        val execution = executionRepository.save(
            AgentProcessExecution(
                agentProcessDefinitionId = definition.id,
                processInstanceId = instance.id,
                nodeId = nodeId,
                status = AgentProcessExecutionStatus.PLANNED,
                inputPayload = inputPayload.toString()
            )
        )

        val outputPayload = buildOutputPayload(execution.id, definition.key, config, inputPayload)
        val decisionTrace = buildDecisionTrace(nodeId, definition, config, inputPayload, outputPayload)

        execution.status = AgentProcessExecutionStatus.COMPLETED
        execution.outputPayload = outputPayload.toString()
        execution.decisionTrace = decisionTrace.toString()
        execution.completedAt = LocalDateTime.now()
        val saved = executionRepository.save(execution)

        upsertVariable(instance.id, "${nodeId}_agentExecutionId", objectMapper.valueToTree(saved.id))
        upsertVariable(instance.id, "${nodeId}_agentDecision", outputPayload.get("decision"))
        applyOutputMappings(instance.id, config, outputPayload)

        return saved
    }

    private fun resolveInputPayload(processInstanceId: Long, config: JsonNode): ObjectNode {
        val payload = objectMapper.createObjectNode()
        val inputs = config.get("inputs")
        if (inputs == null || !inputs.isArray) return payload

        inputs.forEach { mapping ->
            val targetName = mapping.get("targetName")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val source = mapping.get("source")?.asText() ?: "static"
            val valueNode = mapping.get("value")
            val value = when (source) {
                "variable" -> {
                    val variableName = valueNode?.asText()?.trim().orEmpty()
                    processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, variableName)?.value
                        ?: objectMapper.nullNode()
                }
                "static" -> parseStaticValue(valueNode)
                else -> parseStaticValue(valueNode)
            }
            payload.set<JsonNode>(targetName, value)
        }

        return payload
    }

    private fun buildOutputPayload(
        executionId: Long,
        agentProcessKey: String,
        config: JsonNode,
        inputPayload: JsonNode
    ): ObjectNode {
        val output = objectMapper.createObjectNode()
        output.put("executionId", executionId)
        output.put("agentProcessKey", agentProcessKey)
        output.put("status", "COMPLETED")
        output.put("decision", "AGENT_PROCESS_PLANNED")
        output.put(
            "reason",
            "Agent process invocation was recorded for orchestration. Provider execution will be handled by the agent runtime."
        )
        output.set<JsonNode>("inputs", inputPayload)
        config.get("goalOverride")?.asText()?.takeIf { it.isNotBlank() }?.let {
            output.put("goalOverride", it)
        }
        output.put("waitForCompletion", config.get("waitForCompletion")?.asBoolean(true) ?: true)
        config.get("timeoutDays")?.takeIf { it.isNumber }?.let {
            output.put("timeoutDays", it.asInt())
        }
        return output
    }

    private fun buildDecisionTrace(
        nodeId: String,
        definition: com.easy.bpm.model.agent.AgentProcessDefinition,
        config: JsonNode,
        inputPayload: JsonNode,
        outputPayload: JsonNode
    ): ObjectNode {
        val trace = objectMapper.createObjectNode()
        trace.put("nodeId", nodeId)
        trace.put("agentProcessKey", definition.key)
        trace.put("agentProcessVersion", definition.version)
        trace.put("agent", "Agent Runtime")
        trace.set<JsonNode>("input", inputPayload)
        trace.put("decision", outputPayload.get("decision").asText())
        trace.put("reason", outputPayload.get("reason").asText())
        trace.put("tool_called", "agent-process:${definition.key}")
        trace.set<JsonNode>("output", outputPayload)
        trace.put("waitForCompletion", config.get("waitForCompletion")?.asBoolean(true) ?: true)
        trace.put("timestamp", LocalDateTime.now().toString())
        return trace
    }

    private fun applyOutputMappings(processInstanceId: Long, config: JsonNode, outputPayload: JsonNode) {
        val outputs = config.get("outputs")
        if (outputs == null || !outputs.isArray) return

        outputs.forEach { mapping ->
            val targetVariable = mapping.get("targetVariable")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: mapping.get("value")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val sourcePath = mapping.get("sourceValue")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: mapping.get("sourceName")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            upsertVariable(processInstanceId, targetVariable, extractValueByPath(outputPayload, sourcePath))
        }
    }

    private fun extractValueByPath(node: JsonNode, path: String): JsonNode {
        var current: JsonNode? = node
        path.split(".").filter { it.isNotBlank() }.forEach { part ->
            current = current?.get(part)
        }
        return current ?: objectMapper.nullNode()
    }

    private fun upsertVariable(processInstanceId: Long, name: String, value: JsonNode) {
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

    private fun parseStaticValue(valueNode: JsonNode?): JsonNode {
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
        } catch (_: Exception) {
            objectMapper.nodeFactory.textNode(text)
        }
    }
}
