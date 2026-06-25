package com.easy.bpm.ai.factory

import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.ai.provider.AIProviderType
import com.easy.bpm.ai.service.CredentialVault
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Global registry of available AI providers.
 * Allows dynamic registration and discovery of providers.
 */
@Component
class AIProviderRegistry {
    
    private val providers = mutableMapOf<String, KClass<out AIProvider>>()
    
    init {
        // Register built-in providers
        // These will be initialized in phase 9.1 and 9.6
        register("openai", null)           // Phase 9.1.3
        register("anthropic", null)        // Phase 9.6.1
        register("gemini", null)           // Phase 9.6.2
        register("azure-openai", null)     // Phase 9.6.3
        register("custom-rest", null)      // Phase 9.6.4
        register("ollama", null)           // Phase 9.6.4 (pre-configured custom)
    }
    
    /**
     * Register a provider in the registry.
     * 
     * @param providerId Lowercase unique ID (e.g., 'openai')
     * @param providerClass Provider class implementing AIProvider
     */
    fun register(providerId: String, providerClass: KClass<out AIProvider>?) {
        providers[providerId.lowercase()] = providerClass ?: AIProvider::class as KClass<out AIProvider>
    }
    
    /**
     * Get all registered provider IDs.
     * 
     * @return List of provider IDs
     */
    fun getRegisteredProviders(): List<String> = providers.keys.toList()
    
    /**
     * Check if provider is registered.
     * 
     * @param providerId Provider ID
     * @return true if registered
     */
    fun isRegistered(providerId: String): Boolean = providers.containsKey(providerId.lowercase())
    
    /**
     * Get provider class by ID.
     * 
     * @param providerId Provider ID
     * @return Provider class or null if not found
     */
    fun getProviderClass(providerId: String): KClass<out AIProvider>? = 
        providers[providerId.lowercase()]
}

/**
 * Factory for creating AI provider instances.
 * Uses reflection and dependency injection to instantiate providers.
 */
@Component
class AIProviderFactory(
    private val registry: AIProviderRegistry,
    private val credentialVault: CredentialVault
) {
    
    /**
     * Create a provider instance by ID.
     * 
     * @param providerId Provider ID (e.g., 'openai')
     * @param config Provider configuration
     * @param userId User ID for credential access
     * @return AI provider instance
     * @throws IllegalArgumentException if provider not found or config invalid
     */
    fun createProvider(
        providerId: String,
        config: AIProviderConfigDto,
        userId: String
    ): AIProvider {
        val normalizedId = providerId.lowercase()
        
        if (!registry.isRegistered(normalizedId)) {
            throw IllegalArgumentException("Unknown AI provider: '$providerId'. Registered providers: ${registry.getRegisteredProviders()}")
        }
        
        return when (normalizedId) {
            "openai" -> {
                com.easy.bpm.ai.provider.openai.OpenAIProvider(
                    config = config,
                    credentialVault = credentialVault,
                    userId = userId
                )
            }
            "anthropic" -> {
                // Phase 9.6.1: Implement AnthropicProvider
                throw NotImplementedError("Anthropic provider not yet implemented (Phase 9.6.1)")
            }
            "gemini" -> {
                com.easy.bpm.ai.provider.gemini.GeminiProvider(
                    config = config,
                    credentialVault = credentialVault,
                    userId = userId
                )
            }
            "azure-openai" -> {
                // Phase 9.6.3: Implement AzureOpenAIProvider
                throw NotImplementedError("Azure OpenAI provider not yet implemented (Phase 9.6.3)")
            }
            "custom-rest", "ollama" -> {
                // Phase 9.6.4: Implement CustomRESTProvider
                throw NotImplementedError("Custom REST provider not yet implemented (Phase 9.6.4)")
            }
            else -> {
                throw IllegalArgumentException("No factory case for provider: '$normalizedId'")
            }
        }
    }
    
    /**
     * Get provider metadata without instantiating the provider.
     * Used for discovery and UI schema generation.
     * 
     * @param providerId Provider ID
     * @return Provider metadata or null if not found
     */
    fun getProviderMetadata(providerId: String): com.easy.bpm.ai.dto.AIProviderMetadataDto? {
        val normalizedId = providerId.lowercase()
        
        return when (normalizedId) {
            "openai" -> com.easy.bpm.ai.provider.openai.OpenAIProvider.getStaticMetadata()
            "anthropic" -> null  // Not implemented yet
            "gemini" -> com.easy.bpm.ai.provider.gemini.GeminiProvider.getStaticMetadata()
            "azure-openai" -> null // Not implemented yet
            "custom-rest" -> null  // Not implemented yet
            "ollama" -> null       // Not implemented yet
            else -> null
        }
    }
    
    /**
     * Get all available providers with metadata.
     * 
     * @return Map of provider IDs to metadata
     */
    fun getAvailableProviders(): Map<String, com.easy.bpm.ai.dto.AIProviderMetadataDto> {
        return registry.getRegisteredProviders()
            .mapNotNull { providerId ->
                getProviderMetadata(providerId)?.let { metadata ->
                    providerId to metadata
                }
            }
            .toMap()
    }
    
    /**
     * Validate provider configuration without instantiating.
     * 
     * @param providerId Provider ID
     * @param config Configuration to validate
     * @return Validation result
     */
    fun validateConfig(
        providerId: String,
        config: AIProviderConfigDto
    ): com.easy.bpm.ai.dto.ValidationResultDto {
        return when (providerId.lowercase()) {
            "openai" -> com.easy.bpm.ai.provider.openai.OpenAIProvider.validateConfig(config)
            "gemini" -> com.easy.bpm.ai.provider.gemini.GeminiProvider.validateConfig(config)
            else -> {
                com.easy.bpm.ai.dto.ValidationResultDto(
                    valid = false,
                    errors = listOf("Provider validation not implemented: $providerId")
                )
            }
        }
    }
}
