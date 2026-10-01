package com.easy.bpm.service.agent

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.dto.AITuningParamsDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.service.CredentialVault
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.InetAddress
import java.net.URI
import kotlin.system.measureTimeMillis

data class AgentProcessSimulationRequest(
    val definition: JsonNode,
    val inputs: JsonNode? = null
)

data class AgentToolSimulationResult(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val durationMs: Long = 0,
    val response: JsonNode? = null,
    val error: String? = null
)

data class AgentProcessSimulationResponse(
    val success: Boolean,
    val responseText: String = "",
    val providerId: String? = null,
    val modelName: String? = null,
    val tokensUsed: Int = 0,
    val durationMs: Long = 0,
    val toolResults: List<AgentToolSimulationResult> = emptyList(),
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Service
class AgentProcessSimulationService(
    private val agentProcessService: AgentProcessService,
    private val objectMapper: ObjectMapper,
    private val aiProviderFactory: AIProviderFactory,
    private val credentialVault: CredentialVault,
    private val restTemplate: RestTemplate
) {
    fun simulate(request: AgentProcessSimulationRequest): AgentProcessSimulationResponse {
        val definition = agentProcessService.validateDraft(request.definition)
        val inputs = request.inputs ?: objectMapper.createObjectNode()
        require(inputs.isObject) { "Simulation inputs must be a JSON object" }

        val toolResults = executeTools(definition.get("availableTools"), inputs)
        val failedTool = toolResults.firstOrNull { it.status == "FAILED" }
        if (failedTool != null) {
            return AgentProcessSimulationResponse(
                success = false,
                toolResults = toolResults,
                errorCode = "TOOL_EXECUTION_FAILED",
                errorMessage = "Tool '${failedTool.name}' failed: ${failedTool.error ?: "unknown error"}"
            )
        }

        val providerNode = definition.get("provider")
            ?.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Agent Process provider configuration is required for simulation")
        val providerId = providerNode.get("providerId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("provider.providerId is required for simulation")
        val modelName = providerNode.get("modelName")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("provider.modelName is required for simulation")

        val variables = linkedMapOf<String, Any>(
            "goal" to definition.get("goal")?.asText().orEmpty(),
            "instructions" to definition.get("instructions")?.asText().orEmpty(),
            "constraints" to stringifyForPrompt(definition.get("constraints")),
            "tools" to stringifyForPrompt(definition.get("availableTools")),
            "inputs" to inputs.toString(),
            "toolResults" to objectMapper.writeValueAsString(toolResults)
        )
        val promptTemplate = providerNode.get("promptTemplate")?.asText()?.takeIf { it.isNotBlank() }
            ?: defaultPromptTemplate()
        val renderedPrompt = buildString {
            append(substituteTemplate(promptTemplate, variables))
            if (toolResults.isNotEmpty()) {
                append("\n\nVerified tool execution results:\n")
                append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolResults))
                append("\nUse these verified results as authoritative data for the final answer.")
            }
        }
        val providerConfig = AIProviderConfigDto(
            providerId = providerId,
            modelName = modelName,
            endpoint = providerNode.get("endpoint")?.asText()?.trim()?.takeIf { it.isNotEmpty() },
            credentialId = providerNode.get("credentialId")?.asText()?.trim()?.takeIf { it.isNotEmpty() },
            credentialRefName = providerNode.get("credentialRef")?.asText()?.trim()?.takeIf { it.isNotEmpty() },
            timeoutMs = 60_000
        )
        val provider = aiProviderFactory.createProvider(providerId, providerConfig, "agent-process-simulation")
        val response = provider.execute(
            AIExecutionRequestDto(
                promptTemplate = promptTemplate,
                userPrompt = renderedPrompt,
                systemPrompt = providerNode.get("systemPrompt")?.asText(),
                variables = variables,
                tuningParams = extractTuningParams(providerNode.get("tuningParams")),
                providerConfig = providerConfig
            )
        )

        return AgentProcessSimulationResponse(
            success = response.success,
            responseText = response.responseText,
            providerId = providerId,
            modelName = modelName,
            tokensUsed = response.tokensUsed,
            durationMs = response.executionDurationMs,
            toolResults = toolResults,
            errorCode = response.errorCode,
            errorMessage = response.errorMessage
        )
    }

    private fun executeTools(toolsNode: JsonNode?, inputs: JsonNode): List<AgentToolSimulationResult> {
        if (toolsNode == null || !toolsNode.isArray) return emptyList()
        return toolsNode.mapIndexed { index, tool ->
            val id = tool.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: "tool_${index + 1}"
            val name = tool.get("name")?.asText()?.takeIf { it.isNotBlank() } ?: id
            val type = tool.get("type")?.asText().orEmpty()
            if (type != "api-call") {
                AgentToolSimulationResult(
                    id = id,
                    name = name,
                    type = type,
                    status = "SKIPPED",
                    error = "Only API Call tools are executed in the Modeler simulator."
                )
            } else {
                executeApiTool(id, name, tool, inputs)
            }
        }
    }

    private fun executeApiTool(
        id: String,
        name: String,
        tool: JsonNode,
        inputs: JsonNode
    ): AgentToolSimulationResult {
        var responseNode: JsonNode? = null
        var failure: String? = null
        val duration = measureTimeMillis {
            try {
                var url = renderInputTemplate(tool.get("url")?.asText().orEmpty(), inputs)
                require(url.isNotBlank()) { "API tool URL is required" }
                validatePublicHttpUrl(url)

                val headers = HttpHeaders()
                tool.get("headers")?.takeIf { it.isObject }?.fields()?.forEach { (key, value) ->
                    headers[key] = renderInputTemplate(value.asText(), inputs)
                }

                val auth = tool.get("auth")?.takeIf { it.isObject }
                val authType = auth?.get("type")?.asText()?.trim()?.lowercase() ?: "none"
                val authRef = auth?.get("ref")?.asText()?.trim().orEmpty()
                when (authType) {
                    "none", "" -> Unit
                    "bearer" -> headers.setBearerAuth(resolveCredential(authRef))
                    "basic" -> headers.setBasicAuth(
                        resolveCredential("${authRef}_USERNAME"),
                        resolveCredential("${authRef}_PASSWORD")
                    )
                    "apikey" -> {
                        val key = auth?.get("key")?.asText()?.trim().takeUnless { it.isNullOrEmpty() } ?: "X-API-Key"
                        val value = resolveCredential(authRef)
                        if (auth?.get("in")?.asText()?.lowercase() == "query") {
                            url = UriComponentsBuilder.fromHttpUrl(url).queryParam(key, value).build(true).toUriString()
                        } else {
                            headers[key] = value
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported auth type '$authType'")
                }

                val body = tool.get("bodyTemplate")?.takeUnless { it.isNull }?.let {
                    val rendered = renderInputTemplate(if (it.isTextual) it.asText() else it.toString(), inputs)
                    parseJsonOrText(rendered)
                }
                val method = HttpMethod.valueOf(tool.get("method")?.asText("GET")?.uppercase() ?: "GET")
                val response = restTemplate.exchange(URI.create(url), method, HttpEntity(body, headers), String::class.java)
                val responseBody = response.body.orEmpty()
                require(responseBody.length <= MAX_TOOL_RESPONSE_CHARS) {
                    "Tool response exceeded the ${MAX_TOOL_RESPONSE_CHARS / 1024} KB simulation limit"
                }
                responseNode = parseJsonOrText(responseBody)
            } catch (error: Exception) {
                failure = sanitizeError(error)
            }
        }

        return AgentToolSimulationResult(
            id = id,
            name = name,
            type = "api-call",
            status = if (failure == null) "COMPLETED" else "FAILED",
            durationMs = duration,
            response = responseNode,
            error = failure
        )
    }

    private fun validatePublicHttpUrl(value: String) {
        val uri = URI.create(value)
        require(uri.scheme?.lowercase() in setOf("http", "https")) { "Only HTTP and HTTPS tool URLs are supported" }
        require(uri.userInfo == null) { "Tool URLs must not contain embedded credentials" }
        val host = uri.host?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Tool URL must contain a valid host")
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty()) { "Tool URL host could not be resolved" }
        require(addresses.none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }) { "Modeler simulation cannot call local or private network addresses" }
    }

    private fun resolveCredential(reference: String): String {
        require(reference.isNotBlank()) { "Authenticated API tools require a credential reference" }
        return credentialVault.resolveCredentialRef(reference, CredentialVault.WORKSPACE_OWNER_ID)
    }

    private fun renderInputTemplate(template: String, inputs: JsonNode): String {
        var rendered = template
        inputs.fields().forEach { (name, value) ->
            val replacement = if (value.isTextual) value.asText() else value.toString()
            rendered = rendered.replace("{{$name}}", replacement).replace("\${$name}", replacement)
        }
        val missing = TEMPLATE_VARIABLE.findAll(rendered)
            .map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            .distinct()
            .toList()
        require(missing.isEmpty()) { "Missing simulation input(s): ${missing.joinToString(", ")}" }
        return rendered
    }

    private fun parseJsonOrText(value: String): JsonNode =
        try {
            objectMapper.readTree(value)
        } catch (_: Exception) {
            objectMapper.valueToTree(value)
        }

    private fun stringifyForPrompt(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isTextual) return node.asText()
        if (node.isArray) return node.joinToString("\n") { "- ${if (it.isTextual) it.asText() else it}" }
        return node.toString()
    }

    private fun substituteTemplate(template: String, variables: Map<String, Any>): String {
        var rendered = template
        variables.forEach { (name, value) -> rendered = rendered.replace("{{$name}}", value.toString()) }
        return rendered
    }

    private fun extractTuningParams(node: JsonNode?): AITuningParamsDto = AITuningParamsDto(
        temperature = node?.get("temperature")?.asDouble() ?: 0.7,
        topP = node?.get("topP")?.asDouble() ?: 1.0,
        maxTokens = node?.get("maxTokens")?.asInt() ?: 2000,
        frequencyPenalty = node?.get("frequencyPenalty")?.asDouble() ?: 0.0,
        presencePenalty = node?.get("presencePenalty")?.asDouble() ?: 0.0,
        retryCount = node?.get("retryCount")?.asInt() ?: 0,
        backoffMultiplier = node?.get("backoffMultiplier")?.asDouble() ?: 2.0,
        initialDelayMs = node?.get("initialDelayMs")?.asLong() ?: 1000
    )

    private fun sanitizeError(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).replace(Regex("(?i)(token|key|secret)=[^&\\s]+"), "$1=***")

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

        Return an auditable orchestration decision.
        """.trimIndent()

    companion object {
        private const val MAX_TOOL_RESPONSE_CHARS = 65_536
        private val TEMPLATE_VARIABLE = Regex("\\{\\{([^}]+)}}|\\$\\{([^}]+)}")
    }
}
