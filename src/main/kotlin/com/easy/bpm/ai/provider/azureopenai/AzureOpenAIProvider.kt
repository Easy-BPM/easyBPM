package com.easy.bpm.ai.provider.azureopenai

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.dto.AIProviderMetadataDto
import com.easy.bpm.ai.dto.ConfigFieldMetadata
import com.easy.bpm.ai.dto.ValidationResultDto
import com.easy.bpm.ai.provider.AIErrorCode
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.ai.service.CredentialVault
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class AzureOpenAIProvider(
    private val config: AIProviderConfigDto,
    private val credentialVault: CredentialVault,
    private val userId: String,
    private val restTemplate: RestTemplate = RestTemplate()
) : AIProvider() {

    companion object {
        private val logger = LoggerFactory.getLogger(AzureOpenAIProvider::class.java)
        private val mapper = ObjectMapper()

        const val PROVIDER_ID = "azure-openai"
        const val DEFAULT_API_VERSION = "2024-02-15-preview"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        private val supportedModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4",
            "gpt-35-turbo"
        )

        fun getStaticMetadata(): AIProviderMetadataDto = AIProviderMetadataDto(
            providerId = PROVIDER_ID,
            providerName = "Azure OpenAI",
            description = "Azure OpenAI through v1 Responses API or deployment-based chat completions",
            supportedModels = supportedModels,
            defaultModel = DEFAULT_MODEL,
            supportsStreaming = false,
            supportsSystemPrompt = true,
            authTypes = listOf("API_KEY"),
            configFields = mapOf(
                "model" to ConfigFieldMetadata(
                    name = "model",
                    type = "string",
                    required = true,
                    defaultValue = DEFAULT_MODEL,
                    description = "Azure deployment name. It often matches the model name, but Azure routes by deployment."
                ),
                "endpoint" to ConfigFieldMetadata(
                    name = "endpoint",
                    type = "string",
                    required = true,
                    defaultValue = "https://<resource>.openai.azure.com/openai/v1/responses",
                    description = "Azure OpenAI v1 Responses endpoint, v1 base URL, resource base URL, or full chat completions endpoint"
                ),
                "apiVersion" to ConfigFieldMetadata(
                    name = "apiVersion",
                    type = "string",
                    required = false,
                    defaultValue = DEFAULT_API_VERSION,
                    description = "Azure OpenAI api-version query parameter"
                )
            )
        )

        fun validateConfig(config: AIProviderConfigDto): ValidationResultDto {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            if (config.modelName.isBlank()) {
                errors.add("Azure OpenAI deployment/model name is required")
            }

            val endpoint = config.endpoint?.trim()
            if (endpoint.isNullOrBlank()) {
                errors.add("Azure OpenAI endpoint is required")
            } else if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                errors.add("Invalid endpoint URL: must start with http:// or https://")
            }

            if (
                config.apiVersion.isNullOrBlank() &&
                endpoint?.contains("api-version=") != true &&
                endpoint?.contains("/openai/v1") != true
            ) {
                warnings.add("Azure OpenAI apiVersion was not provided; using default $DEFAULT_API_VERSION")
            }

            if (config.timeoutMs < 1000) {
                errors.add("Timeout must be at least 1000ms")
            }
            if (config.timeoutMs > 600000) {
                warnings.add("Timeout >600s may cause long-running processes")
            }

            return ValidationResultDto(
                valid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )
        }
    }

    override fun getProviderId(): String = PROVIDER_ID

    override fun getMetadata(): AIProviderMetadataDto = getStaticMetadata()

    override fun validateConfig(request: AIExecutionRequestDto): ValidationResultDto =
        validateConfig(request.providerConfig)

    override fun execute(request: AIExecutionRequestDto): AIExecutionResponseDto {
        val startTime = Instant.now().toEpochMilli()

        return try {
            val validation = validateConfig(request.providerConfig)
            if (!validation.valid) {
                return AIExecutionResponseDto(
                    responseText = "",
                    success = false,
                    errorMessage = validation.errors.joinToString("; "),
                    errorCode = AIErrorCode.INVALID_CONFIG
                )
            }

            val apiKey = credentialVault.resolveCredentialRef(
                request.providerConfig.credentialRefName ?: request.providerConfig.credentialId ?: "",
                userId
            )
            val prompt = renderPrompt(
                request.userPrompt?.takeIf { it.isNotBlank() } ?: request.promptTemplate,
                request.variables
            )
            val endpoint = buildEndpoint(request.providerConfig)
            val requestBody = buildAzureRequest(
                userPrompt = prompt,
                systemPrompt = request.systemPrompt,
                request = request,
                endpointType = endpoint.type
            )
            val response = sendRequest(requestBody, apiKey, endpoint.url)
            val parsed = mapper.readTree(response)
            val resultText = extractResponseText(parsed, endpoint.type)
            val promptTokens = extractPromptTokens(parsed, endpoint.type)
            val completionTokens = extractCompletionTokens(parsed, endpoint.type)
            val totalTokens = extractTotalTokens(parsed, promptTokens, completionTokens)
            val duration = Instant.now().toEpochMilli() - startTime

            logger.info(
                "Azure OpenAI execution successful: model={}, endpointType={}, tokens={}, duration={}ms",
                request.providerConfig.modelName,
                endpoint.type,
                totalTokens,
                duration
            )

            AIExecutionResponseDto(
                responseText = resultText,
                tokensUsed = totalTokens,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                executionDurationMs = duration,
                success = true,
                metadata = mapOf(
                    "provider" to PROVIDER_ID,
                    "model" to request.providerConfig.modelName,
                    "endpointType" to endpoint.type.name,
                    "finishReason" to extractFinishReason(parsed, endpoint.type)
                )
            )
        } catch (e: Exception) {
            val duration = Instant.now().toEpochMilli() - startTime
            val errorCode = classifyError(e)
            val errorMsg = sanitizeErrorMessage(e)

            logger.error("Azure OpenAI execution failed: $errorCode - $errorMsg", e)

            AIExecutionResponseDto(
                responseText = "",
                executionDurationMs = duration,
                success = false,
                errorMessage = errorMsg,
                errorCode = errorCode
            )
        }
    }

    private fun renderPrompt(template: String, variables: Map<String, Any>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{{$key}}", value.toString())
        }
        return result
    }

    private fun buildEndpoint(config: AIProviderConfigDto): AzureEndpoint {
        val endpoint = config.endpoint!!.trim()
        if (endpoint.contains("/responses")) {
            return AzureEndpoint(endpoint, AzureEndpointType.RESPONSES)
        }

        if (endpoint.trimEnd('/').endsWith("/openai/v1")) {
            return AzureEndpoint(endpoint.trimEnd('/') + "/responses", AzureEndpointType.RESPONSES)
        }

        if (endpoint.contains("/chat/completions")) {
            return AzureEndpoint(appendApiVersion(endpoint, config.apiVersion), AzureEndpointType.CHAT_COMPLETIONS)
        }

        val deployment = URLEncoder.encode(config.modelName, StandardCharsets.UTF_8)
        val base = endpoint.trimEnd('/')
        return AzureEndpoint(appendApiVersion(
            "$base/openai/deployments/$deployment/chat/completions",
            config.apiVersion
        ), AzureEndpointType.CHAT_COMPLETIONS)
    }

    private fun appendApiVersion(endpoint: String, apiVersion: String?): String {
        if (endpoint.contains("api-version=")) {
            return endpoint
        }

        val separator = if (endpoint.contains("?")) "&" else "?"
        return endpoint + separator + "api-version=" + (apiVersion?.takeIf { it.isNotBlank() } ?: DEFAULT_API_VERSION)
    }

    private fun buildAzureRequest(
        userPrompt: String,
        systemPrompt: String?,
        request: AIExecutionRequestDto,
        endpointType: AzureEndpointType
    ): String {
        if (endpointType == AzureEndpointType.RESPONSES) {
            val requestMap = mutableMapOf<String, Any>(
                "model" to request.providerConfig.modelName,
                "input" to userPrompt,
                "temperature" to request.tuningParams.temperature,
                "top_p" to request.tuningParams.topP,
                "max_output_tokens" to request.tuningParams.maxTokens
            )

            if (!systemPrompt.isNullOrBlank()) {
                requestMap["instructions"] = systemPrompt
            }

            return mapper.writeValueAsString(requestMap)
        }

        val messages = mutableListOf<Map<String, String>>()
        if (!systemPrompt.isNullOrBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to userPrompt))

        val requestMap = mapOf(
            "messages" to messages,
            "temperature" to request.tuningParams.temperature,
            "top_p" to request.tuningParams.topP,
            "max_tokens" to request.tuningParams.maxTokens,
            "frequency_penalty" to request.tuningParams.frequencyPenalty,
            "presence_penalty" to request.tuningParams.presencePenalty
        )

        return mapper.writeValueAsString(requestMap)
    }

    private fun sendRequest(requestBody: String, apiKey: String, endpoint: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("api-key", apiKey)
            set("User-Agent", "EasyBPM/1.0")
        }

        return try {
            restTemplate.postForObject(endpoint, HttpEntity(requestBody, headers), String::class.java)
                ?: throw RuntimeException("Empty response from Azure OpenAI")
        } catch (e: HttpClientErrorException) {
            throw e
        } catch (e: HttpServerErrorException) {
            throw e
        } catch (e: ResourceAccessException) {
            throw e
        }
    }

    private fun extractJsonPath(node: JsonNode, path: String): String {
        var current = node
        for (segment in path.split(".")) {
            current = if (segment.contains("[")) {
                val key = segment.substring(0, segment.indexOf("["))
                val index = segment.substring(segment.indexOf("[") + 1, segment.indexOf("]")).toInt()
                current.get(key)?.get(index) ?: return ""
            } else {
                current.get(segment) ?: return ""
            }
        }
        return current.asText("")
    }

    private fun extractResponseText(node: JsonNode, endpointType: AzureEndpointType): String {
        if (endpointType == AzureEndpointType.CHAT_COMPLETIONS) {
            return extractJsonPath(node, "choices[0].message.content")
        }

        node.get("output_text")?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        node.at("/output/0/content/0/text").asText("").takeIf { it.isNotBlank() }?.let { return it }

        val textParts = mutableListOf<String>()
        node.get("output")?.forEach { outputItem ->
            outputItem.get("content")?.forEach { content ->
                content.get("text")?.asText()?.takeIf { it.isNotBlank() }?.let(textParts::add)
            }
        }
        return textParts.joinToString("\n").trim()
    }

    private fun extractPromptTokens(node: JsonNode, endpointType: AzureEndpointType): Int =
        if (endpointType == AzureEndpointType.RESPONSES) {
            node.at("/usage/input_tokens").asInt(0)
        } else {
            node.at("/usage/prompt_tokens").asInt(0)
        }

    private fun extractCompletionTokens(node: JsonNode, endpointType: AzureEndpointType): Int =
        if (endpointType == AzureEndpointType.RESPONSES) {
            node.at("/usage/output_tokens").asInt(0)
        } else {
            node.at("/usage/completion_tokens").asInt(0)
        }

    private fun extractTotalTokens(node: JsonNode, promptTokens: Int, completionTokens: Int): Int =
        node.at("/usage/total_tokens").asInt(promptTokens + completionTokens)

    private fun extractFinishReason(node: JsonNode, endpointType: AzureEndpointType): String =
        if (endpointType == AzureEndpointType.RESPONSES) {
            node.get("status")?.asText("unknown") ?: "unknown"
        } else {
            node.at("/choices/0/finish_reason").asText("unknown")
        }

    private fun classifyError(e: Exception): String {
        return when (e) {
            is HttpClientErrorException -> when (e.statusCode.value()) {
                401, 403 -> AIErrorCode.AUTH_ERROR
                404 -> AIErrorCode.MODEL_NOT_FOUND
                429 -> AIErrorCode.RATE_LIMIT
                else -> AIErrorCode.PROVIDER_ERROR
            }
            is HttpServerErrorException -> AIErrorCode.PROVIDER_ERROR
            is SocketTimeoutException -> AIErrorCode.TIMEOUT
            is ResourceAccessException -> AIErrorCode.NETWORK_ERROR
            else -> AIErrorCode.UNKNOWN
        }
    }

    private fun sanitizeErrorMessage(e: Exception): String {
        val msg = e.message ?: "Unknown error"
        return msg.replace(Regex("""(?i)(api-key=)[^&\s]+"""), "$1[REDACTED_TOKEN]")
    }
}

private data class AzureEndpoint(
    val url: String,
    val type: AzureEndpointType
)

private enum class AzureEndpointType {
    RESPONSES,
    CHAT_COMPLETIONS
}
