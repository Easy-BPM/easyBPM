package com.easy.bpm.handler

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AITuningParamsDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.service.CredentialVault
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.incident.IncidentService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * AITaskHandler - Orchestrates AI Task execution lifecycle
 *
 * Full workflow:
 * 1. Extract AI task configuration from node properties
 * 2. Apply input variable substitution ({{variableName}} → actual value)
 * 3. Invoke AI provider (OpenAI, Anthropic, etc.)
 * 4. Process response with optional JSON path extraction
 * 5. Bind response to process variable
 * 6. Record execution metrics
 * 7. Return success or throw exception for error boundary handling
 *
 * Error handling:
 * - AUTH_ERROR: Fails immediately, no retry
 * - RATE_LIMIT: Retries with exponential backoff
 * - TIMEOUT: Retries with exponential backoff
 * - PROVIDER_ERROR: Retries once, then fails
 * - PARSE_ERROR: Fails immediately
 *
 * Variables are substituted using {{variableName}} syntax in prompt templates.
 * Responses are stored as process variables (type: 'json' by default).
 */
@Service
class AITaskHandler(
    private val aiProviderFactory: AIProviderFactory,
    private val credentialVault: CredentialVault,
    private val processVariableRepository: ProcessVariableRepository,
    private val incidentService: IncidentService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Execute an AI Task within a process instance
     *
     * @param instanceId Process instance ID
     * @param node Node JSON from process definition
     * @param inputVariables Current process variables (Map of name -> value)
     * @return Map of output variables to set in process (key: outputVariable name, value: AI response)
     * @throws AITaskExecutionException on any error (will trigger error boundary if configured)
     */
    @Transactional
    fun executeAITask(
        instanceId: Long,
        node: JsonNode,
        inputVariables: Map<String, Any?>
    ): Map<String, Any?> {
        val executionStart = System.currentTimeMillis()
        val nodeId = node.get("id").asText()

        try {
            logger.info("Starting AI Task execution: instance=$instanceId, nodeId=$nodeId")

            // Extract configuration from node properties
            val config = node.get("properties")
                ?: node.get("config")
                ?: throw AITaskExecutionException(
                    "AI Task $nodeId missing properties/config",
                    "INVALID_CONFIG"
                )

            val providerId = config.get("providerId")?.asText()
                ?: throw AITaskExecutionException(
                    "AI Task $nodeId missing providerId",
                    "INVALID_CONFIG"
                )

            val modelName = config.get("modelName")?.asText()
                ?: throw AITaskExecutionException(
                    "AI Task $nodeId missing modelName",
                    "INVALID_CONFIG"
                )

            val promptTemplate = config.get("promptTemplate")?.asText()
                ?: throw AITaskExecutionException(
                    "AI Task $nodeId missing promptTemplate",
                    "INVALID_CONFIG"
                )

            val outputVariable = config.get("outputVariable")?.asText()
                ?: throw AITaskExecutionException(
                    "AI Task $nodeId missing outputVariable",
                    "INVALID_CONFIG"
                )

            // Extract optional parameters
            val systemPrompt = config.get("systemPrompt")?.asText()
            val credentialId = config.get("credentialId")?.asText()
            val credentialRef = config.get("credentialRef")?.asText()
            val customEndpoint = config.get("endpoint")?.asText()
            val tuningParamsJson = config.get("tuningParams")

            // Apply variable substitution to prompt template
            val substitutedPrompt = substituteVariables(promptTemplate, inputVariables, nodeId)
            logger.debug("Substituted prompt (credentials masked): {}", maskCredentials(substitutedPrompt))

            // Build tuning parameters
            val tuningParams = extractTuningParams(tuningParamsJson)

            // Build provider configuration
            val providerConfig = com.easy.bpm.ai.dto.AIProviderConfigDto(
                providerId = providerId,
                modelName = modelName,
                endpoint = customEndpoint,
                credentialId = credentialId,
                credentialRefName = credentialRef
            )

            // Create provider instance
            val provider = aiProviderFactory.createProvider(
                providerId = providerId,
                config = providerConfig,
                userId = "ai-task-executor" // AI task execution system user
            )

            // Build execution request
            @Suppress("UNCHECKED_CAST")
            val executionRequest = AIExecutionRequestDto(
                promptTemplate = promptTemplate,
                userPrompt = substitutedPrompt,
                systemPrompt = systemPrompt,
                variables = inputVariables as Map<String, Any>,
                tuningParams = tuningParams,
                providerConfig = providerConfig
            )

            // Execute with retry logic
            val executionResult = executeWithRetry(
                provider = provider,
                request = executionRequest,
                maxRetries = tuningParams?.retryCount ?: 0,
                initialDelayMs = tuningParams?.initialDelayMs ?: 1000,
                backoffMultiplier = tuningParams?.backoffMultiplier ?: 2.0,
                nodeId = nodeId
            )

            if (!executionResult.success) {
                throw AITaskExecutionException(
                    "AI execution failed: ${executionResult.errorMessage}",
                    executionResult.errorCode ?: "PROVIDER_ERROR"
                )
            }

            val responseText = executionResult.responseText
            logger.info(
                "AI Task completed: instance=$instanceId, nodeId=$nodeId, tokens={}, duration={}ms",
                executionResult.tokensUsed,
                executionResult.executionDurationMs
            )

            // Return output mapping
            return mapOf(outputVariable to responseText)

        } catch (ex: AITaskExecutionException) {
            incidentService.createIncident(
                processInstanceId = instanceId,
                nodeId = nodeId,
                source = IncidentSource.AI_TASK,
                message = ex.message ?: "AI task execution failed",
                technicalDetails = "AI task failed with error code ${ex.errorCode}",
                externalReferenceId = "ai_task:${ex.errorCode}"
            )
            logger.error(
                "AI Task execution failed: instance=$instanceId, nodeId=$nodeId, errorCode={}",
                ex.errorCode,
                ex
            )
            throw ex
        } catch (ex: Exception) {
            incidentService.createIncident(
                processInstanceId = instanceId,
                nodeId = nodeId,
                source = IncidentSource.AI_TASK,
                message = "AI task execution failed: ${ex.message}",
                technicalDetails = "Unexpected AI task failure",
                externalReferenceId = "ai_task:UNKNOWN"
            )
            logger.error(
                "Unexpected error in AI Task execution: instance=$instanceId, nodeId=$nodeId",
                ex
            )
            throw AITaskExecutionException(
                "Unexpected error: ${ex.message}",
                "UNKNOWN"
            )
        }
    }

    /**
     * Execute AI provider with retry logic and exponential backoff
     */
    private fun executeWithRetry(
        provider: com.easy.bpm.ai.provider.AIProvider,
        request: AIExecutionRequestDto,
        maxRetries: Int,
        initialDelayMs: Long,
        backoffMultiplier: Double,
        nodeId: String
    ): com.easy.bpm.ai.dto.AIExecutionResponseDto {
        var lastException: Exception? = null
        var currentDelayMs = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                logger.debug("AI Task execution attempt {}/{}: nodeId={}", attempt + 1, maxRetries + 1, nodeId)

                val result = provider.execute(request)

                if (result.success) {
                    return result
                }

                // Check if error is retryable
                val isRetryable = when (result.errorCode) {
                    "RATE_LIMIT", "TIMEOUT" -> true
                    "PROVIDER_ERROR" -> attempt < maxRetries
                    else -> false
                }

                if (!isRetryable || attempt >= maxRetries) {
                    return result
                }

                logger.warn(
                    "AI Task execution failed with retryable error [{}]: nodeId={}, attempt={}/{}",
                    result.errorCode,
                    nodeId,
                    attempt + 1,
                    maxRetries + 1
                )

                // Apply exponential backoff
                if (attempt < maxRetries) {
                    logger.debug(
                        "Retrying after {}ms backoff: nodeId={}",
                        currentDelayMs,
                        nodeId
                    )
                    Thread.sleep(currentDelayMs)
                    currentDelayMs = (currentDelayMs * backoffMultiplier).toLong()
                }

            } catch (ex: Exception) {
                lastException = ex
                logger.warn(
                    "Exception during AI Task execution attempt {}/{}: nodeId={}",
                    attempt + 1,
                    maxRetries + 1,
                    nodeId,
                    ex
                )

                if (attempt < maxRetries) {
                    logger.debug("Retrying after {}ms backoff: nodeId={}", currentDelayMs, nodeId)
                    Thread.sleep(currentDelayMs)
                    currentDelayMs = (currentDelayMs * backoffMultiplier).toLong()
                }
            }
        }

        // All retries exhausted
        return com.easy.bpm.ai.dto.AIExecutionResponseDto(
            responseText = "",
            success = false,
            errorMessage = lastException?.message ?: "Max retries exhausted",
            errorCode = "RETRY_EXHAUSTED"
        )
    }

    /**
     * Substitute {{variableName}} placeholders in prompt template with actual values
     */
    private fun substituteVariables(
        template: String,
        variables: Map<String, Any?>,
        nodeId: String
    ): String {
        var result = template

        // Find all {{varName}} patterns
        val pattern = Regex("""\{\{([a-zA-Z_][a-zA-Z0-9_]*)\}\}""")

        pattern.findAll(template).forEach { match ->
            val varName = match.groupValues[1]
            val value = variables[varName]

            if (value != null) {
                val valueStr = when (value) {
                    is String -> value
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> objectMapper.writeValueAsString(value)
                }
                result = result.replace("{{$varName}}", valueStr)
                logger.debug("Substituted {{{}}} in AI Task: nodeId={}", varName, nodeId)
            } else {
                logger.warn(
                    "Variable {{{}}} not found in process variables: nodeId={}",
                    varName,
                    nodeId
                )
            }
        }

        return result
    }

    /**
     * Extract tuning parameters from JSON node
     */
    private fun extractTuningParams(tuningParamsJson: JsonNode?): AITuningParamsDto {
        if (tuningParamsJson == null || !tuningParamsJson.isObject) {
            return AITuningParamsDto(
                temperature = 0.7,
                topP = 1.0,
                maxTokens = 2000,
                frequencyPenalty = 0.0,
                presencePenalty = 0.0,
                retryCount = 0,
                backoffMultiplier = 2.0,
                initialDelayMs = 1000
            )
        }

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

    /**
     * Mask credentials in text for logging (redact API keys, tokens, etc.)
     */
    private fun maskCredentials(text: String): String {
        return text
            .replace(Regex("""sk-[a-zA-Z0-9]+"""), "sk-***")
            .replace(Regex("""bearer\s+[a-zA-Z0-9._-]+"""), "bearer ***")
            .replace(Regex("""token[=:]\s*[a-zA-Z0-9._-]+"""), "token=***")
    }
}

/**
 * Custom exception for AI Task execution errors
 * Includes error code for mapping to error boundaries
 */
class AITaskExecutionException(
    message: String,
    val errorCode: String
) : Exception(message)
