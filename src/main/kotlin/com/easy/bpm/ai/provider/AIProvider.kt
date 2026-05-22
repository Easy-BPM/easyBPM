package com.easy.bpm.ai.provider

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.dto.AIProviderMetadataDto
import com.easy.bpm.ai.dto.ValidationResultDto

/**
 * Abstract AI Provider interface.
 * All AI providers must implement this contract.
 * Supports async/future-based execution.
 */
abstract class AIProvider {
    
    /**
     * Execute an AI task with the given request.
     * 
     * @param request AI execution request containing prompt, variables, tuning params
     * @return AI execution response with result or error
     * @throws IllegalArgumentException if config validation fails
     * @throws java.util.concurrent.TimeoutException if execution exceeds timeout
     */
    abstract fun execute(request: AIExecutionRequestDto): AIExecutionResponseDto
    
    /**
     * Validate provider configuration before execution.
     * 
     * @param request Request containing provider config to validate
     * @return Validation result with errors/warnings if applicable
     */
    abstract fun validateConfig(request: AIExecutionRequestDto): ValidationResultDto
    
    /**
     * Get provider metadata (models, capabilities, config schema).
     * Used for discovery and UI rendering.
     * 
     * @return Provider metadata including available models and UI hints
     */
    abstract fun getMetadata(): AIProviderMetadataDto
    
    /**
     * Get provider ID (lowercase, no spaces).
     * Used for factory lookup: 'openai', 'anthropic', 'gemini', etc.
     * 
     * @return Provider ID
     */
    abstract fun getProviderId(): String
    
    /**
     * Optional: Health check endpoint (if provider has public health).
     * Used by admin monitoring dashboard.
     * 
     * @return true if provider is reachable, false otherwise
     */
    open fun healthCheck(): Boolean = true
}

/**
 * Error codes for AI execution failures.
 * Used for retry logic and error boundary routing.
 */
object AIErrorCode {
    const val AUTH_ERROR = "AUTH_ERROR"                 // 401, invalid token
    const val RATE_LIMIT = "RATE_LIMIT"                 // 429, retry with backoff
    const val TIMEOUT = "TIMEOUT"                        // Request exceeded timeout
    const val PROVIDER_ERROR = "PROVIDER_ERROR"          // 500+, transient server error
    const val PARSE_ERROR = "PARSE_ERROR"                // Response parsing failed
    const val INVALID_CONFIG = "INVALID_CONFIG"          // Config validation failed
    const val NETWORK_ERROR = "NETWORK_ERROR"            // Connection refused, unreachable
    const val MODEL_NOT_FOUND = "MODEL_NOT_FOUND"        // Requested model not available
    const val QUOTA_EXCEEDED = "QUOTA_EXCEEDED"          // Quota/budget limit exceeded
    const val UNKNOWN = "UNKNOWN"                        // Unknown error
}

/**
 * Credential types for AI provider authentication.
 */
enum class AICredentialType {
    API_KEY,        // Single API key (e.g., OpenAI sk-...)
    BEARER,         // Bearer token (Authorization: Bearer <token>)
    BASIC_AUTH,     // Basic auth (username:password)
    OAUTH2,         // OAuth2 token (future)
    MANAGED_IDENTITY // Azure managed identity (future)
}

/**
 * AI Provider enumeration for registration and discovery.
 */
enum class AIProviderType(val providerId: String, val displayName: String) {
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    GEMINI("gemini", "Google Gemini"),
    AZURE_OPENAI("azure-openai", "Azure OpenAI"),
    OLLAMA("ollama", "Ollama (Local)"),
    CUSTOM_REST("custom-rest", "Custom REST API");
    
    companion object {
        fun fromId(id: String): AIProviderType? = values().find { it.providerId == id }
    }
}
