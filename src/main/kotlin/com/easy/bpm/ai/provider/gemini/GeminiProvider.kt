package com.easy.bpm.ai.provider.gemini

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
import java.time.Instant

class GeminiProvider(
    private val config: AIProviderConfigDto,
    private val credentialVault: CredentialVault,
    private val userId: String,
    private val restTemplate: RestTemplate = RestTemplate()
) : AIProvider() {

    companion object {
        private val logger = LoggerFactory.getLogger(GeminiProvider::class.java)
        private val mapper = ObjectMapper()

        const val PROVIDER_ID = "gemini"
        const val DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val DEFAULT_MODEL = "gemini-3.5-flash"

        private val supportedModels = listOf(
            "gemini-3.5-flash",
            "gemini-3-flash",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-flash-latest"
        )

        fun getStaticMetadata(): AIProviderMetadataDto = AIProviderMetadataDto(
            providerId = PROVIDER_ID,
            providerName = "Google Gemini",
            description = "Google Gemini models through the Gemini Interactions API",
            supportedModels = supportedModels,
            defaultModel = DEFAULT_MODEL,
            supportsStreaming = false,
            supportsSystemPrompt = true,
            authTypes = listOf("API_KEY"),
            configFields = mapOf(
                "model" to ConfigFieldMetadata(
                    name = "model",
                    type = "select",
                    required = true,
                    defaultValue = DEFAULT_MODEL,
                    options = supportedModels
                ),
                "endpoint" to ConfigFieldMetadata(
                    name = "endpoint",
                    type = "string",
                    required = false,
                    defaultValue = DEFAULT_ENDPOINT,
                    description = "Gemini Interactions API endpoint"
                )
            )
        )

        fun validateConfig(config: AIProviderConfigDto): ValidationResultDto {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            if (!supportedModels.contains(config.modelName)) {
                errors.add("Invalid Gemini model: ${config.modelName}. Valid: $supportedModels")
            }

            if (!config.endpoint.isNullOrBlank()) {
                if (!config.endpoint.startsWith("http://") && !config.endpoint.startsWith("https://")) {
                    errors.add("Invalid endpoint URL: must start with http:// or https://")
                }
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

    override fun validateConfig(request: AIExecutionRequestDto): ValidationResultDto {
        return validateConfig(request.providerConfig)
    }

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
            val renderedPrompt = renderPrompt(request.promptTemplate, request.variables)
            val input = request.userPrompt?.takeIf { it.isNotBlank() } ?: renderedPrompt
            val requestBody = buildGeminiRequest(
                input = input,
                systemPrompt = request.systemPrompt,
                model = request.providerConfig.modelName,
                request = request
            )
            val response = sendRequest(
                requestBody = requestBody,
                apiKey = apiKey,
                endpoint = request.providerConfig.endpoint ?: DEFAULT_ENDPOINT
            )

            val parsed = mapper.readTree(response)
            val resultText = extractOutputText(parsed)
            val promptTokens = parsed.at("/usage_metadata/prompt_token_count").asInt(0)
            val completionTokens = parsed.at("/usage_metadata/candidates_token_count").asInt(0)
            val totalTokens = parsed.at("/usage_metadata/total_token_count").asInt(promptTokens + completionTokens)
            val duration = Instant.now().toEpochMilli() - startTime

            logger.info(
                "Gemini execution successful: model={}, tokens={}, duration={}ms",
                request.providerConfig.modelName,
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
                metadata = mapOf("model" to request.providerConfig.modelName)
            )
        } catch (e: Exception) {
            val duration = Instant.now().toEpochMilli() - startTime
            val errorCode = classifyError(e)
            val errorMsg = sanitizeErrorMessage(e)

            logger.error("Gemini execution failed: $errorCode - $errorMsg", e)

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

    private fun buildGeminiRequest(
        input: String,
        systemPrompt: String?,
        model: String,
        request: AIExecutionRequestDto
    ): String {
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "input" to input,
            "generation_config" to mapOf(
                "temperature" to request.tuningParams.temperature,
                "top_p" to request.tuningParams.topP,
                "max_output_tokens" to request.tuningParams.maxTokens
            )
        )

        if (!systemPrompt.isNullOrBlank()) {
            requestMap["system_instruction"] = systemPrompt
        }

        return mapper.writeValueAsString(requestMap)
    }

    private fun sendRequest(requestBody: String, apiKey: String, endpoint: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("x-goog-api-key", apiKey)
            set("User-Agent", "EasyBPM/1.0")
        }

        return try {
            restTemplate.postForObject(endpoint, HttpEntity(requestBody, headers), String::class.java)
                ?: throw RuntimeException("Empty response from Gemini")
        } catch (e: HttpClientErrorException) {
            throw e
        } catch (e: HttpServerErrorException) {
            throw e
        } catch (e: ResourceAccessException) {
            throw e
        }
    }

    private fun extractOutputText(node: JsonNode): String {
        node.get("output_text")?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        node.at("/candidates/0/content/parts/0/text").asText("").takeIf { it.isNotBlank() }?.let { return it }

        val textParts = mutableListOf<String>()
        node.get("steps")?.forEach { step ->
            step.get("content")?.forEach { content ->
                content.get("text")?.asText()?.takeIf { it.isNotBlank() }?.let(textParts::add)
            }
        }
        return textParts.joinToString("\n").trim()
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
        return msg
            .replace(Regex("""AIza[0-9A-Za-z_-]+"""), "[REDACTED_TOKEN]")
            .replace(Regex("""key=[0-9A-Za-z_-]+"""), "key=[REDACTED_TOKEN]")
    }
}
