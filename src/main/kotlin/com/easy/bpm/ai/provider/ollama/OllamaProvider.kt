package com.easy.bpm.ai.provider.ollama

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

class OllamaProvider(
    private val config: AIProviderConfigDto,
    private val credentialVault: CredentialVault,
    private val userId: String,
    private val restTemplate: RestTemplate = RestTemplate()
) : AIProvider() {

    companion object {
        private val logger = LoggerFactory.getLogger(OllamaProvider::class.java)
        private val mapper = ObjectMapper()

        const val PROVIDER_ID = "ollama"
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_ENDPOINT = "$DEFAULT_BASE_URL/api/generate"
        const val DEFAULT_MODEL = "llama3.2"

        private val supportedModels = listOf(
            "llama3.2",
            "llama3.1",
            "mistral",
            "qwen2.5",
            "phi3"
        )

        fun getStaticMetadata(): AIProviderMetadataDto = AIProviderMetadataDto(
            providerId = PROVIDER_ID,
            providerName = "Ollama (Local)",
            description = "Local Ollama runtime for development and customer-safe local testing",
            supportedModels = supportedModels,
            defaultModel = DEFAULT_MODEL,
            supportsStreaming = false,
            supportsSystemPrompt = true,
            authTypes = emptyList(),
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
                    defaultValue = DEFAULT_BASE_URL,
                    description = "Ollama base URL or full /api/generate endpoint"
                )
            )
        )

        fun validateConfig(config: AIProviderConfigDto): ValidationResultDto {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            if (config.modelName.isBlank()) {
                errors.add("Ollama modelName is required")
            }

            val endpoint = config.endpoint?.trim()
            if (!endpoint.isNullOrBlank() && !endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                errors.add("Invalid endpoint URL: must start with http:// or https://")
            }

            if (config.timeoutMs < 1000) {
                errors.add("Timeout must be at least 1000ms")
            }
            if (config.timeoutMs > 600000) {
                warnings.add("Timeout >600s may cause long-running processes")
            }

            if (config.credentialId != null || config.credentialRefName != null) {
                warnings.add("Ollama local testing usually does not require credentials; configured credentials will be ignored")
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

            val renderedPrompt = renderPrompt(
                request.userPrompt?.takeIf { it.isNotBlank() } ?: request.promptTemplate,
                request.variables
            )
            val endpoint = normalizeEndpoint(request.providerConfig.endpoint)
            val requestBody = buildOllamaRequest(
                prompt = renderedPrompt,
                systemPrompt = request.systemPrompt,
                model = request.providerConfig.modelName.ifBlank { DEFAULT_MODEL },
                request = request
            )
            val response = sendRequest(requestBody, endpoint)

            val parsed = mapper.readTree(response)
            val resultText = parsed.get("response")?.asText("").orEmpty()
            val promptTokens = parsed.get("prompt_eval_count")?.asInt(0) ?: 0
            val completionTokens = parsed.get("eval_count")?.asInt(0) ?: 0
            val totalTokens = promptTokens + completionTokens
            val duration = Instant.now().toEpochMilli() - startTime

            logger.info(
                "Ollama execution successful: model={}, endpoint={}, duration={}ms",
                request.providerConfig.modelName,
                endpoint,
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
                    "model" to request.providerConfig.modelName,
                    "endpoint" to endpoint,
                    "doneReason" to parsed.get("done_reason")?.asText("").orEmpty()
                )
            )
        } catch (e: Exception) {
            val duration = Instant.now().toEpochMilli() - startTime
            val errorCode = classifyError(e)
            val errorMsg = sanitizeErrorMessage(e)

            logger.error("Ollama execution failed: $errorCode - $errorMsg", e)

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

    private fun normalizeEndpoint(rawEndpoint: String?): String {
        val endpoint = rawEndpoint?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_BASE_URL
        return if (endpoint.endsWith("/api/generate")) endpoint else endpoint.trimEnd('/') + "/api/generate"
    }

    private fun buildOllamaRequest(
        prompt: String,
        systemPrompt: String?,
        model: String,
        request: AIExecutionRequestDto
    ): String {
        val payload = mutableMapOf<String, Any>(
            "model" to model,
            "prompt" to prompt,
            "stream" to false,
            "options" to mapOf(
                "temperature" to request.tuningParams.temperature,
                "top_p" to request.tuningParams.topP,
                "num_predict" to request.tuningParams.maxTokens
            )
        )

        if (!systemPrompt.isNullOrBlank()) {
            payload["system"] = systemPrompt
        }

        return mapper.writeValueAsString(payload)
    }

    private fun sendRequest(requestBody: String, endpoint: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("User-Agent", "EasyBPM/1.0")
        }

        return try {
            restTemplate.postForObject(endpoint, HttpEntity(requestBody, headers), String::class.java)
                ?: throw RuntimeException("Empty response from Ollama")
        } catch (e: HttpClientErrorException) {
            throw e
        } catch (e: HttpServerErrorException) {
            throw e
        } catch (e: ResourceAccessException) {
            throw e
        }
    }

    private fun classifyError(e: Exception): String {
        return when (e) {
            is HttpClientErrorException -> when (e.statusCode.value()) {
                404 -> AIErrorCode.MODEL_NOT_FOUND
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
        return msg.replace(Regex("""http://localhost:11434/api/generate"""), DEFAULT_ENDPOINT)
    }
}
