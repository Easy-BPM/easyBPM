package com.easy.bpm.handler

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.dto.AITuningParamsDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.model.agent.AgentProcessExecution
import com.easy.bpm.model.agent.AgentProcessExecutionStatus
import com.easy.bpm.model.agent.AgentProcessDefinition
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
    private val objectMapper: ObjectMapper,
    private val aiProviderFactory: AIProviderFactory
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

        val agentDefinitionJson = objectMapper.readTree(definition.definitionJson)
        val outputPayload = executeAgentOrPlan(execution.id, definition, agentDefinitionJson, config, inputPayload)
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

    private fun executeAgentOrPlan(
        executionId: Long,
        definition: AgentProcessDefinition,
        agentDefinitionJson: JsonNode,
        config: JsonNode,
        inputPayload: JsonNode
    ): ObjectNode {
        val providerConfigNode = agentDefinitionJson.get("provider")
        return if (providerConfigNode != null && providerConfigNode.isObject) {
            executeProviderBackedAgent(executionId, definition.key, agentDefinitionJson, providerConfigNode, config, inputPayload)
        } else {
            buildPlannedOutputPayload(executionId, definition.key, config, inputPayload)
        }
    }

    private fun executeProviderBackedAgent(
        executionId: Long,
        agentProcessKey: String,
        agentDefinitionJson: JsonNode,
        providerConfigNode: JsonNode,
        invocationConfig: JsonNode,
        inputPayload: JsonNode
    ): ObjectNode {
        val providerId = providerConfigNode.get("providerId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Agent process '$agentProcessKey' provider missing providerId")
        val modelName = providerConfigNode.get("modelName")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Agent process '$agentProcessKey' provider missing modelName")
        val credentialId = providerConfigNode.get("credentialId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        val credentialRef = providerConfigNode.get("credentialRef")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        val endpoint = providerConfigNode.get("endpoint")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        val systemPrompt = providerConfigNode.get("systemPrompt")?.asText()
        val promptTemplate = providerConfigNode.get("promptTemplate")?.asText()?.takeIf { it.isNotBlank() }
            ?: defaultPromptTemplate()

        val variables = buildAgentVariables(agentDefinitionJson, invocationConfig, inputPayload)
        val renderedPrompt = substituteTemplate(promptTemplate, variables)
        val providerConfig = AIProviderConfigDto(
            providerId = providerId,
            modelName = modelName,
            endpoint = endpoint,
            credentialId = credentialId,
            credentialRefName = credentialRef
        )
        val provider = aiProviderFactory.createProvider(providerId, providerConfig, "agent-process-runtime")
        val response = provider.execute(
            AIExecutionRequestDto(
                promptTemplate = promptTemplate,
                userPrompt = renderedPrompt,
                systemPrompt = systemPrompt,
                variables = variables,
                tuningParams = extractTuningParams(providerConfigNode.get("tuningParams")),
                providerConfig = providerConfig
            )
        )

        if (!response.success) {
            throw IllegalStateException("Agent process provider execution failed: ${response.errorMessage ?: response.errorCode ?: "unknown error"}")
        }

        val output = objectMapper.createObjectNode()
        output.put("executionId", executionId)
        output.put("agentProcessKey", agentProcessKey)
        output.put("status", "COMPLETED")
        output.put("decision", "AGENT_PROCESS_DECIDED")
        output.put("reason", "Agent provider '$providerId' produced an orchestration decision.")
        output.put("providerId", providerId)
        output.put("modelName", modelName)
        output.put("responseText", response.responseText)
        output.put("tokensUsed", response.tokensUsed)
        mergeProviderResponseJson(output, response.responseText)
        output.set<JsonNode>("inputs", inputPayload)
        invocationConfig.get("goalOverride")?.asText()?.takeIf { it.isNotBlank() }?.let {
            output.put("goalOverride", it)
        }
        return output
    }

    private fun buildPlannedOutputPayload(
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

    private fun buildAgentVariables(
        agentDefinitionJson: JsonNode,
        invocationConfig: JsonNode,
        inputPayload: JsonNode
    ): Map<String, Any> {
        val variables = linkedMapOf<String, Any>()
        variables["goal"] = invocationConfig.get("goalOverride")?.asText()?.takeIf { it.isNotBlank() }
            ?: agentDefinitionJson.get("goal")?.asText().orEmpty()
        variables["instructions"] = agentDefinitionJson.get("instructions")?.asText().orEmpty()
        variables["constraints"] = stringifyForPrompt(agentDefinitionJson.get("constraints"))
        variables["tools"] = stringifyForPrompt(agentDefinitionJson.get("availableTools"))
        variables["participants"] = stringifyForPrompt(agentDefinitionJson.get("participants"))
        variables["inputs"] = inputPayload.toString()
        return variables
    }

    private fun stringifyForPrompt(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isTextual) return node.asText()
        if (node.isArray) return node.joinToString("\n") { "- ${if (it.isTextual) it.asText() else it.toString()}" }
        return node.toString()
    }

    private fun substituteTemplate(template: String, variables: Map<String, Any>): String {
        var rendered = template
        variables.forEach { (name, value) ->
            rendered = rendered.replace("{{$name}}", value.toString())
        }
        return rendered
    }

    private fun extractTuningParams(tuningParamsJson: JsonNode?): AITuningParamsDto {
        if (tuningParamsJson == null || !tuningParamsJson.isObject) return AITuningParamsDto()
        return AITuningParamsDto(
            temperature = tuningParamsJson.get("temperature")?.asDouble() ?: 0.7,
            topP = tuningParamsJson.get("topP")?.asDouble() ?: 1.0,
            maxTokens = tuningParamsJson.get("maxTokens")?.asInt() ?: 2000,
            frequencyPenalty = tuningParamsJson.get("frequencyPenalty")?.asDouble() ?: 0.0,
            presencePenalty = tuningParamsJson.get("presencePenalty")?.asDouble() ?: 0.0,
            retryCount = tuningParamsJson.get("retryCount")?.asInt() ?: 0,
            backoffMultiplier = tuningParamsJson.get("backoffMultiplier")?.asDouble() ?: 2.0,
            initialDelayMs = tuningParamsJson.get("initialDelayMs")?.asLong() ?: 1000
        )
    }

    private fun defaultPromptTemplate(): String =
        """
        Goal: {{goal}}
        Instructions: {{instructions}}
        Constraints:
        {{constraints}}
        Available tools:
        {{tools}}
        Inputs:
        {{inputs}}

        Return an auditable orchestration decision
        """.trimIndent()

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
            current = parseTextualJson(current)
            current = current?.get(part)
        }
        return current ?: objectMapper.nullNode()
    }

    private fun parseTextualJson(node: JsonNode?): JsonNode? {
        if (node == null || !node.isTextual) return node
        val text = node.asText().trim()
        if (!text.startsWith("{") && !text.startsWith("[")) return node
        return try {
            objectMapper.readTree(text)
        } catch (_: Exception) {
            node
        }
    }

    private fun mergeProviderResponseJson(output: ObjectNode, responseText: String) {
        val parsed = parseProviderResponseJson(responseText) ?: return
        output.set<JsonNode>("agentResponse", parsed)
        if (!parsed.isObject) return

        parsed.properties().forEach { (fieldName, fieldValue) ->
            if (!output.has(fieldName)) {
                output.set<JsonNode>(fieldName, fieldValue)
            }
        }
    }

    private fun parseProviderResponseJson(responseText: String): JsonNode? {
        val trimmed = responseText.trim()
        if (trimmed.isBlank()) return null

        val candidates = mutableListOf<String>()
        candidates.add(when {
            trimmed.startsWith("```") -> {
                trimmed
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }
            else -> trimmed
        })

        extractJsonCandidate(trimmed)?.let(candidates::add)

        candidates.forEach { candidate ->
            try {
                return objectMapper.readTree(candidate)
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun extractJsonCandidate(text: String): String? {
        val objectStart = text.indexOf('{')
        val arrayStart = text.indexOf('[')
        val start = listOf(objectStart, arrayStart).filter { it >= 0 }.minOrNull() ?: return null
        val opener = text[start]
        val closer = if (opener == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\' && inString) {
                escaped = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (char == opener) depth++
            if (char == closer) depth--
            if (depth == 0) {
                return text.substring(start, index + 1)
            }
        }

        return null
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
