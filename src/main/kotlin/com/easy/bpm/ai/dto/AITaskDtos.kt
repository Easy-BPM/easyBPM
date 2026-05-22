package com.easy.bpm.ai.dto

import java.io.Serializable

/**
 * AI Provider configuration transferred from modeler UI or API.
 * Does not contain sensitive credentials (stored separately in vault).
 */
data class AIProviderConfigDto(
    val providerId: String,                          // 'openai', 'anthropic', 'gemini', 'azure-openai', 'ollama', 'custom-rest'
    val modelName: String,                           // e.g., 'gpt-4', 'claude-3-opus', 'gemini-pro'
    val endpoint: String? = null,                    // Custom endpoint for self-hosted or custom providers
    val apiVersion: String? = null,                  // For Azure and versioned APIs
    val timeoutMs: Long = 30000,                     // Execution timeout in milliseconds
    val streamingEnabled: Boolean = false,           // Enable streaming response (if supported by provider)
    val credentialId: String? = null,                // UUID reference to stored credential in vault
    val credentialRefName: String? = null,           // Environment variable reference: $VAR_NAME
    val customHeaders: Map<String, String> = emptyMap() // For custom providers
) : Serializable

/**
 * Execution request sent to AI provider.
 */
data class AIExecutionRequestDto(
    val promptTemplate: String,                      // Template with {{variable}} placeholders
    val userPrompt: String? = null,                  // User-facing message
    val systemPrompt: String? = null,                // System role for the AI
    val variables: Map<String, Any> = emptyMap(),   // Process variables for injection
    val tuningParams: AITuningParamsDto = AITuningParamsDto(),
    val providerConfig: AIProviderConfigDto
) : Serializable

/**
 * AI tuning parameters (temperature, top_p, max_tokens, etc).
 */
data class AITuningParamsDto(
    val temperature: Double = 0.7,                   // 0.0–2.0 (OpenAI range)
    val topP: Double = 1.0,                          // 0.0–1.0
    val maxTokens: Int = 2000,                       // Output token limit
    val frequencyPenalty: Double = 0.0,              // −2.0–2.0
    val presencePenalty: Double = 0.0,               // −2.0–2.0
    val retryCount: Int = 0,                         // Number of retries on transient failure
    val backoffMultiplier: Double = 2.0,             // Exponential backoff factor
    val initialDelayMs: Long = 1000                  // Initial retry delay
) : Serializable

/**
 * Response from AI provider after execution.
 */
data class AIExecutionResponseDto(
    val responseText: String,                        // Full AI response
    val tokensUsed: Int = 0,                         // Total tokens consumed
    val promptTokens: Int = 0,                       // Input tokens
    val completionTokens: Int = 0,                   // Output tokens
    val executionDurationMs: Long = 0,               // Wall-clock time in milliseconds
    val success: Boolean = true,
    val errorMessage: String? = null,                // Sanitized error (no credentials)
    val errorCode: String? = null,                   // Error classification: AUTH_ERROR, RATE_LIMIT, TIMEOUT, etc.
    val metadata: Map<String, Any> = emptyMap()     // Provider-specific metadata
) : Serializable

/**
 * Provider metadata for discovery and UI presentation.
 */
data class AIProviderMetadataDto(
    val providerId: String,
    val providerName: String,                        // Display name
    val description: String? = null,
    val supportedModels: List<String>,               // Available models
    val defaultModel: String,                        // Default when not specified
    val supportsStreaming: Boolean = false,
    val supportsSystemPrompt: Boolean = true,
    val authTypes: List<String> = listOf("API_KEY", "BEARER"), // Supported credential types
    val configFields: Map<String, ConfigFieldMetadata> = emptyMap() // UI schema hints
) : Serializable

/**
 * Metadata for a configuration field (for UI schema rendering).
 */
data class ConfigFieldMetadata(
    val name: String,
    val type: String,                                // 'string', 'number', 'boolean', 'select'
    val required: Boolean = false,
    val defaultValue: String? = null,
    val description: String? = null,
    val options: List<String> = emptyList()         // For select type
) : Serializable

/**
 * Masked credential DTO for API responses (never exposes full token).
 */
data class AICredentialResponseDto(
    val id: String,                                  // UUID
    val providerId: String,
    val credentialType: String,                      // API_KEY, BEARER, BASIC_AUTH
    val maskedToken: String,                         // Last 4 chars only, e.g., "sk-***...hfaX"
    val createdAt: String,
    val updatedAt: String,
    val lastUsedAt: String? = null,
    val permissions: List<String> = emptyList()
) : Serializable

/**
 * Request to store a new credential.
 */
data class AICredentialCreateRequestDto(
    val providerId: String,
    val credentialType: String,                      // API_KEY, BEARER, BASIC_AUTH
    val token: String                                // Will be encrypted immediately server-side
) : Serializable

/**
 * Validation result from provider config validation.
 */
data class ValidationResultDto(
    val valid: Boolean = true,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) : Serializable
