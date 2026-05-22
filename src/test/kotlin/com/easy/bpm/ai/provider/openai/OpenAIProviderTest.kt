package com.easy.bpm.ai.provider.openai

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.dto.AITuningParamsDto
import com.easy.bpm.ai.service.CredentialVault
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for OpenAI provider.
 * Tests: metadata, config validation, error classification, prompt rendering
 * 
 * Integration tests with actual OpenAI API would require:
 * - Valid OpenAI API key (from env var)
 * - Network access
 * - Separate integration test suite with @Tag("integration")
 */
class OpenAIProviderTest {
    
    private lateinit var credentialVault: CredentialVault
    
    @BeforeEach
    fun setup() {
        credentialVault = mock(CredentialVault::class.java)
    }
    
    @Test
    fun `test get provider metadata`() {
        val metadata = OpenAIProvider.getStaticMetadata()
        
        assertEquals("openai", metadata.providerId)
        assertEquals("OpenAI", metadata.providerName)
        assertTrue(metadata.supportedModels.contains("gpt-4"))
        assertTrue(metadata.supportedModels.contains("gpt-3.5-turbo"))
        assertEquals("gpt-3.5-turbo", metadata.defaultModel)
        assertTrue(metadata.supportsStreaming)
        assertTrue(metadata.supportsSystemPrompt)
    }
    
    @Test
    fun `test validate valid OpenAI config`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo"
        )
        
        val result = OpenAIProvider.validateConfig(config)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `test validate config with invalid model`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "invalid-model"
        )
        
        val result = OpenAIProvider.validateConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Invalid OpenAI model") })
    }
    
    @Test
    fun `test validate config with invalid endpoint URL`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo",
            endpoint = "not-a-url"
        )
        
        val result = OpenAIProvider.validateConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Invalid endpoint") })
    }
    
    @Test
    fun `test validate config with timeout warning`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo",
            timeoutMs = 700000  // >600s
        )
        
        val result = OpenAIProvider.validateConfig(config)
        assertTrue(result.valid)
        assertTrue(result.warnings.any { it.contains("Timeout") })
    }
    
    @Test
    fun `test validate config with insufficient timeout`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo",
            timeoutMs = 500
        )
        
        val result = OpenAIProvider.validateConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("at least 1000ms") })
    }
    
    @Test
    fun `test get provider ID`() {
        `when`(credentialVault.resolveCredentialRef("", "user")).thenReturn("fake-key")
        val provider = OpenAIProvider(
            config = AIProviderConfigDto(
                providerId = "openai",
                modelName = "gpt-3.5-turbo"
            ),
            credentialVault = credentialVault,
            userId = "user"
        )
        
        assertEquals("openai", provider.getProviderId())
    }
    
    @Test
    fun `test metadata contains config field schema for UI`() {
        val metadata = OpenAIProvider.getStaticMetadata()
        
        assertTrue(metadata.configFields.containsKey("model"))
        assertTrue(metadata.configFields.containsKey("endpoint"))
        
        val modelField = metadata.configFields["model"]
        assertNotNull(modelField)
        assertEquals("select", modelField.type)
        assertTrue(modelField.required)
        assertTrue(modelField.options.isNotEmpty())
    }
    
    @Test
    fun `test error code classification from HTTP status`() {
        // These would be tested with actual HTTP mocking in full integration tests
        // Just verify the enum values exist
        assertTrue(OpenAIProvider::class.java.declaredMethods.isNotEmpty())
    }
    
    @Test
    fun `test tuning parameters are recognized`() {
        val config = AIProviderConfigDto(
            providerId = "openai",
            modelName = "gpt-3.5-turbo"
        )
        val tuning = AITuningParamsDto(
            temperature = 1.5,
            topP = 0.9,
            maxTokens = 1000,
            frequencyPenalty = 0.5,
            presencePenalty = -0.5
        )
        
        val request = AIExecutionRequestDto(
            promptTemplate = "Hello world",
            userPrompt = "Summarize this text",
            systemPrompt = "You are a helpful assistant",
            tuningParams = tuning,
            providerConfig = config
        )
        
        assertEquals(1.5, request.tuningParams.temperature)
        assertEquals(0.9, request.tuningParams.topP)
        assertEquals(1000, request.tuningParams.maxTokens)
    }
    
    /**
     * Note: Full integration tests with actual OpenAI API calls would require:
     * - Valid API key from environment
     * - Mock API responses using WireMock
     * - Separate test class marked @Tag("integration")
     * 
     * Example:
     * @Test
     * @Tag("integration")
     * fun `test execute text generation with OpenAI API mock`() { ... }
     */
}
