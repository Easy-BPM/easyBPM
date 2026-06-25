package com.easy.bpm.ai.factory

import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.provider.gemini.GeminiProvider
import com.easy.bpm.ai.provider.ollama.OllamaProvider
import com.easy.bpm.ai.provider.openai.OpenAIProvider
import com.easy.bpm.ai.service.CredentialVault
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import kotlin.test.assertTrue

/**
 * Unit tests for AIProviderFactory and AIProviderRegistry.
 * Tests: provider registration, instantiation, metadata discovery, validation
 */
class AIProviderFactoryTest {
    
    private lateinit var registry: AIProviderRegistry
    private lateinit var credentialVault: CredentialVault
    private lateinit var factory: AIProviderFactory
    
    @BeforeEach
    fun setup() {
        registry = AIProviderRegistry()
        credentialVault = mock(CredentialVault::class.java)
        factory = AIProviderFactory(registry, credentialVault)
    }
    
    @Test
    fun `test registry initialization contains expected providers`() {
        val providers = registry.getRegisteredProviders()
        assertTrue(providers.contains("openai"))
        assertTrue(providers.contains("anthropic"))
        assertTrue(providers.contains("gemini"))
        assertTrue(providers.contains("azure-openai"))
        assertTrue(providers.contains("custom-rest"))
        assertTrue(providers.contains("ollama"))
    }
    
    @Test
    fun `test is registered check`() {
        assertTrue(registry.isRegistered("openai"))
        assertTrue(registry.isRegistered("OPENAI"))  // Case-insensitive
        assertTrue(!registry.isRegistered("unknown-provider"))
    }
    
    @Test
    fun `test create OpenAI provider`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo"
        )
        
        val provider = factory.createProvider("openai", config, "user123")
        assertTrue(provider is OpenAIProvider)
        assertTrue(provider.getProviderId() == "openai")
    }

    @Test
    fun `test create Gemini provider`() {
        val config = AIProviderConfigDto(
            providerId = "gemini",
            modelName = "gemini-3.5-flash"
        )

        val provider = factory.createProvider("gemini", config, "user123")
        assertTrue(provider is GeminiProvider)
        assertTrue(provider.getProviderId() == "gemini")
    }

    @Test
    fun `test create Ollama provider`() {
        val config = AIProviderConfigDto(
            providerId = "ollama",
            modelName = "llama3.2"
        )

        val provider = factory.createProvider("ollama", config, "user123")
        assertTrue(provider is OllamaProvider)
        assertTrue(provider.getProviderId() == "ollama")
    }
    
    @Test
    fun `test create provider with invalid provider ID throws exception`() {
        val config = AIProviderConfigDto(
            providerId = "unknown",
            modelName = "test"
        )
        
        assertThrows<IllegalArgumentException> {
            factory.createProvider("unknown", config, "user123")
        }
    }
    
    @Test
    fun `test get provider metadata for OpenAI`() {
        val metadata = factory.getProviderMetadata("openai")
        assertTrue(metadata != null)
        assertTrue(metadata!!.providerId == "openai")
        assertTrue(metadata.supportedModels.contains("gpt-4"))
        assertTrue(metadata.supportsStreaming)
    }

    @Test
    fun `test get provider metadata for Gemini`() {
        val metadata = factory.getProviderMetadata("gemini")
        assertTrue(metadata != null)
        assertTrue(metadata!!.providerId == "gemini")
        assertTrue(metadata.supportedModels.contains("gemini-3.5-flash"))
    }

    @Test
    fun `test get provider metadata for Ollama`() {
        val metadata = factory.getProviderMetadata("ollama")
        assertTrue(metadata != null)
        assertTrue(metadata!!.providerId == "ollama")
        assertTrue(metadata.supportedModels.contains("llama3.2"))
    }
    
    @Test
    fun `test validate OpenAI config with invalid model`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "invalid-model"
        )
        
        val result = factory.validateConfig("openai", config)
        assertTrue(!result.valid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test validate OpenAI config with valid model`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo"
        )
        
        val result = factory.validateConfig("openai", config)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate Gemini config with valid model`() {
        val config = AIProviderConfigDto(
            providerId = "gemini",
            modelName = "gemini-3.5-flash"
        )

        val result = factory.validateConfig("gemini", config)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate Ollama config with valid model`() {
        val config = AIProviderConfigDto(
            providerId = "ollama",
            modelName = "llama3.2",
            endpoint = "http://localhost:11434"
        )

        val result = factory.validateConfig("ollama", config)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `test validate config with invalid endpoint`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo",
            endpoint = "not-a-valid-url"
        )
        
        val result = factory.validateConfig("openai", config)
        assertTrue(!result.valid)
    }
    
    @Test
    fun `test get available providers includes metadata`() {
        val availableProviders = factory.getAvailableProviders()
        assertTrue(availableProviders.containsKey("openai"))
        assertTrue(availableProviders.containsKey("gemini"))
        assertTrue(availableProviders.containsKey("ollama"))
        assertTrue(availableProviders["openai"]?.supportedModels?.isNotEmpty() == true)
    }
}
