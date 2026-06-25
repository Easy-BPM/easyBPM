package com.easy.bpm.ai.provider.gemini

import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.service.CredentialVault
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeminiProviderTest {

    private lateinit var credentialVault: CredentialVault

    @BeforeEach
    fun setup() {
        credentialVault = mock(CredentialVault::class.java)
    }

    @Test
    fun `test get provider metadata`() {
        val metadata = GeminiProvider.getStaticMetadata()

        assertEquals("gemini", metadata.providerId)
        assertEquals("Google Gemini", metadata.providerName)
        assertTrue(metadata.supportedModels.contains("gemini-3.5-flash"))
        assertEquals("gemini-3.5-flash", metadata.defaultModel)
        assertTrue(metadata.supportsSystemPrompt)
        assertFalse(metadata.supportsStreaming)
    }

    @Test
    fun `test validate valid Gemini config`() {
        val config = AIProviderConfigDto(
            providerId = "gemini",
            modelName = "gemini-3.5-flash"
        )

        val result = GeminiProvider.validateConfig(config)

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate config with invalid model`() {
        val config = AIProviderConfigDto(
            providerId = "gemini",
            modelName = "gemini-pro"
        )

        val result = GeminiProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Invalid Gemini model") })
    }

    @Test
    fun `test validate config with invalid endpoint URL`() {
        val config = AIProviderConfigDto(
            providerId = "gemini",
            modelName = "gemini-3.5-flash",
            endpoint = "not-a-url"
        )

        val result = GeminiProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Invalid endpoint") })
    }

    @Test
    fun `test get provider ID`() {
        val provider = GeminiProvider(
            config = AIProviderConfigDto(
                providerId = "gemini",
                modelName = "gemini-3.5-flash"
            ),
            credentialVault = credentialVault,
            userId = "user"
        )

        assertEquals("gemini", provider.getProviderId())
    }

    @Test
    fun `test metadata contains config field schema for UI`() {
        val metadata = GeminiProvider.getStaticMetadata()

        assertTrue(metadata.configFields.containsKey("model"))
        assertTrue(metadata.configFields.containsKey("endpoint"))

        val modelField = metadata.configFields["model"]
        assertNotNull(modelField)
        assertEquals("select", modelField.type)
        assertTrue(modelField.required)
        assertTrue(modelField.options.contains("gemini-3.5-flash"))
    }
}
