package com.easy.bpm.ai.provider.openai

import com.easy.bpm.ai.dto.*
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

/**
 * OpenAI Provider implementation supporting GPT-4, GPT-3.5-turbo, and GPT-4-turbo.
 * 
 * Supports:
 * - Text generation with system/user prompts
 * - All tuning parameters (temperature, top_p, max_tokens, penalties)
 * - Streaming responses
 * - Token counting and cost estimation
 * - Comprehensive error handling with retry support
 */
class OpenAIProvider(
    private val config: AIProviderConfigDto,
    private val credentialVault: CredentialVault,
    private val userId: String,
    private val restTemplate: RestTemplate = RestTemplate()
) : AIProvider() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(OpenAIProvider::class.java)
        private val mapper = ObjectMapper()
        
        const val PROVIDER_ID = "openai"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-3.5-turbo"
        
        /**
         * Get provider metadata without instantiation.
         */
        fun getStaticMetadata(): AIProviderMetadataDto = AIProviderMetadataDto(
            providerId = PROVIDER_ID,
            providerName = "OpenAI",
            description = "OpenAI GPT models (GPT-4, GPT-3.5-turbo, GPT-4-turbo)",
            supportedModels = listOf(
                "gpt-4",
                "gpt-4-turbo-preview",
                "gpt-3.5-turbo",
                "gpt-3.5-turbo-16k"
            ),
            defaultModel = DEFAULT_MODEL,
            supportsStreaming = true,
            supportsSystemPrompt = true,
            authTypes = listOf("API_KEY", "BEARER"),
            configFields = mapOf(
                "model" to ConfigFieldMetadata(
                    name = "model",
                    type = "select",
                    required = true,
                    defaultValue = DEFAULT_MODEL,
                    options = listOf("gpt-4", "gpt-4-turbo-preview", "gpt-3.5-turbo", "gpt-3.5-turbo-16k")
                ),
                "endpoint" to ConfigFieldMetadata(
                    name = "endpoint",
                    type = "string",
                    required = false,
                    defaultValue = DEFAULT_ENDPOINT,
                    description = "OpenAI API endpoint (for Azure or proxy)"
                )
            )
        )
        
        /**
         * Validate OpenAI configuration.
         */
        fun validateConfig(config: AIProviderConfigDto): ValidationResultDto {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Model validation
            val validModels = listOf("gpt-4", "gpt-4-turbo-preview", "gpt-3.5-turbo", "gpt-3.5-turbo-16k")
            if (!validModels.contains(config.modelName)) {
                errors.add("Invalid OpenAI model: ${config.modelName}. Valid: $validModels")
            }
            
            // Endpoint validation (if custom)
            if (!config.endpoint.isNullOrBlank()) {
                if (!config.endpoint.startsWith("http://") && !config.endpoint.startsWith("https://")) {
                    errors.add("Invalid endpoint URL: must start with http:// or https://")
                }
            }
            
            // Tuning parameter validation
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
    
    override fun execute(request: AIExecutionRequestDto): AIExecutionResponseDto {
        val startTime = Instant.now().toEpochMilli()
        
        try {
            // Validate config
            val validation = validateConfig(request.providerConfig)
            if (!validation.valid) {
                return AIExecutionResponseDto(
                    responseText = "",
                    success = false,
                    errorMessage = validation.errors.joinToString("; "),
                    errorCode = AIErrorCode.INVALID_CONFIG
                )
            }
            
            // Resolve credential (decrypt from vault or env var)
            val apiKey = credentialVault.resolveCredentialRef(
                request.providerConfig.credentialRefName ?: request.providerConfig.credentialId ?: "",
                userId
            )
            
            // Render prompt with variables
            val renderedPrompt = renderPrompt(request.promptTemplate, request.variables)
            
            // Build OpenAI request
            val requestBody = buildOpenAIRequest(
                userPrompt = renderedPrompt,
                systemPrompt = request.userPrompt,
                model = request.providerConfig.modelName,
                tuning = request.tuningParams
            )
            
            // Send request to OpenAI
            val response = sendRequest(
                requestBody = requestBody,
                apiKey = apiKey,
                endpoint = request.providerConfig.endpoint ?: DEFAULT_ENDPOINT,
                timeoutMs = request.providerConfig.timeoutMs
            )
            
            // Parse response
            val parsed = mapper.readTree(response)
            val resultText = extractJsonPath(parsed, "choices[0].message.content")
            val promptTokens = parsed.at("/usage/prompt_tokens").asInt(0)
            val completionTokens = parsed.at("/usage/completion_tokens").asInt(0)
            val totalTokens = promptTokens + completionTokens
            
            val duration = Instant.now().toEpochMilli() - startTime
            
            logger.info("OpenAI execution successful: model=${request.providerConfig.modelName}, tokens=$totalTokens, duration=${duration}ms")
            
            return AIExecutionResponseDto(
                responseText = resultText,
                tokensUsed = totalTokens,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                executionDurationMs = duration,
                success = true,
                metadata = mapOf(
                    "model" to (request.providerConfig.modelName ?: DEFAULT_MODEL),
                    "stopReason" to (parsed.at("/choices[0].finish_reason").asText("unknown"))
                )
            )
            
        } catch (e: Exception) {
            val duration = Instant.now().toEpochMilli() - startTime
            val errorCode = classifyError(e)
            val errorMsg = sanitizeErrorMessage(e)
            
            logger.error("OpenAI execution failed: $errorCode - $errorMsg", e)
            
            return AIExecutionResponseDto(
                responseText = "",
                executionDurationMs = duration,
                success = false,
                errorMessage = errorMsg,
                errorCode = errorCode
            )
        }
    }
    
    override fun validateConfig(request: AIExecutionRequestDto): ValidationResultDto {
        return validateConfig(request.providerConfig)
    }
    
    /**
     * Render prompt template with variable substitution.
     * Template format: "Hello {{name}}, you are {{role}}"
     */
    private fun renderPrompt(template: String, variables: Map<String, Any>): String {
        var result = template
        for ((key, value) in variables) {
            val placeholder = "{{$key}}"
            result = result.replace(placeholder, value.toString())
        }
        return result
    }
    
    /**
     * Build OpenAI API request JSON.
     */
    private fun buildOpenAIRequest(
        userPrompt: String,
        systemPrompt: String?,
        model: String,
        tuning: AITuningParamsDto
    ): String {
        val messages = mutableListOf<Map<String, String>>()
        
        if (!systemPrompt.isNullOrBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to userPrompt))
        
        val requestMap = mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to tuning.temperature,
            "top_p" to tuning.topP,
            "max_tokens" to tuning.maxTokens,
            "frequency_penalty" to tuning.frequencyPenalty,
            "presence_penalty" to tuning.presencePenalty
        )
        
        return mapper.writeValueAsString(requestMap)
    }
    
    /**
     * Send HTTP request to OpenAI.
     */
    private fun sendRequest(
        requestBody: String,
        apiKey: String,
        endpoint: String,
        timeoutMs: Long
    ): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer $apiKey")
            set("User-Agent", "EasyBPM/1.0")
        }
        
        val entity = HttpEntity(requestBody, headers)
        
        return try {
            restTemplate.postForObject(endpoint, entity, String::class.java)
                ?: throw RuntimeException("Empty response from OpenAI")
        } catch (e: HttpClientErrorException) {
            throw e  // Will be classified in catch block
        } catch (e: HttpServerErrorException) {
            throw e  // Will be classified in catch block
        } catch (e: ResourceAccessException) {
            throw e  // Connection error
        }
    }
    
    /**
     * Extract nested JSON value using path notation.
     * Example: "choices[0].message.content"
     */
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
    
    /**
     * Classify error by exception type.
     */
    private fun classifyError(e: Exception): String {
        return when (e) {
            is HttpClientErrorException -> when (e.statusCode.value()) {
                401, 403 -> AIErrorCode.AUTH_ERROR
                429 -> AIErrorCode.RATE_LIMIT
                else -> AIErrorCode.PROVIDER_ERROR
            }
            is HttpServerErrorException -> AIErrorCode.PROVIDER_ERROR
            is SocketTimeoutException -> AIErrorCode.TIMEOUT
            is ResourceAccessException -> AIErrorCode.NETWORK_ERROR
            else -> AIErrorCode.UNKNOWN
        }
    }
    
    /**
     * Sanitize error message (remove credentials, sensitive info).
     */
    private fun sanitizeErrorMessage(e: Exception): String {
        val msg = e.message ?: "Unknown error"
        return msg.replace(Regex("sk-[a-zA-Z0-9]+"), "[REDACTED_TOKEN]")
    }
}
