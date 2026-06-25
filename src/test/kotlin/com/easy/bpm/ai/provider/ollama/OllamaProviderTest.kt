package com.easy.bpm.ai.provider.ollama

import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.service.CredentialVault
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaProviderTest {

    private lateinit var credentialVault: CredentialVault

    @BeforeEach
    fun setup() {
        credentialVault = mock(CredentialVault::class.java)
    }

    @Test
    fun `test get provider metadata`() {
        val metadata = OllamaProvider.getStaticMetadata()

        assertEquals("ollama", metadata.providerId)
        assertEquals("Ollama (Local)", metadata.providerName)
        assertTrue(metadata.supportedModels.contains("llama3.2"))
        assertEquals("llama3.2", metadata.defaultModel)
        assertTrue(metadata.supportsSystemPrompt)
        assertFalse(metadata.supportsStreaming)
    }

    @Test
    fun `test validate valid Ollama config`() {
        val config = AIProviderConfigDto(
            providerId = "ollama",
            modelName = "llama3.2",
            endpoint = "http://localhost:11434"
        )

        val result = OllamaProvider.validateConfig(config)

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate config with blank model`() {
        val config = AIProviderConfigDto(
            providerId = "ollama",
            modelName = ""
        )

        val result = OllamaProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("modelName") })
    }

    @Test
    fun `test validate config with invalid endpoint URL`() {
        val config = AIProviderConfigDto(
            providerId = "ollama",
            modelName = "llama3.2",
            endpoint = "localhost:11434"
        )

        val result = OllamaProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Invalid endpoint") })
    }

    @Test
    fun `test get provider ID`() {
        val provider = OllamaProvider(
            config = AIProviderConfigDto(
                providerId = "ollama",
                modelName = "llama3.2"
            ),
            credentialVault = credentialVault,
            userId = "user"
        )

        assertEquals("ollama", provider.getProviderId())
    }

    @Test
    fun `test metadata contains config field schema for UI`() {
        val metadata = OllamaProvider.getStaticMetadata()

        assertTrue(metadata.configFields.containsKey("model"))
        assertTrue(metadata.configFields.containsKey("endpoint"))

        val endpointField = metadata.configFields["endpoint"]
        assertNotNull(endpointField)
        assertEquals("string", endpointField.type)
        assertEquals("http://localhost:11434", endpointField.defaultValue)
    }
}
