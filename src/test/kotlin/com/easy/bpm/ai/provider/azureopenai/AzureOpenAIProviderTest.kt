package com.easy.bpm.ai.provider.azureopenai

import com.easy.bpm.ai.dto.AIProviderConfigDto
import com.easy.bpm.ai.service.CredentialVault
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AzureOpenAIProviderTest {

    private lateinit var credentialVault: CredentialVault

    @BeforeEach
    fun setup() {
        credentialVault = mock(CredentialVault::class.java)
    }

    @Test
    fun `test get provider metadata`() {
        val metadata = AzureOpenAIProvider.getStaticMetadata()

        assertEquals("azure-openai", metadata.providerId)
        assertEquals("Azure OpenAI", metadata.providerName)
        assertEquals("gpt-4o-mini", metadata.defaultModel)
        assertTrue(metadata.supportsSystemPrompt)
        assertTrue(metadata.configFields.containsKey("endpoint"))
        assertTrue(metadata.configFields.containsKey("apiVersion"))
    }

    @Test
    fun `test validate valid Azure OpenAI config`() {
        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "claims-agent-deployment",
            endpoint = "https://example-resource.openai.azure.com",
            apiVersion = "2024-02-15-preview"
        )

        val result = AzureOpenAIProvider.validateConfig(config)

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate config requires endpoint`() {
        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "claims-agent-deployment"
        )

        val result = AzureOpenAIProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("endpoint is required") })
    }

    @Test
    fun `test validate config requires deployment name`() {
        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "",
            endpoint = "https://example-resource.openai.azure.com"
        )

        val result = AzureOpenAIProvider.validateConfig(config)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("deployment/model name is required") })
    }

    @Test
    fun `test validate full endpoint with api version does not warn about default api version`() {
        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "claims-agent-deployment",
            endpoint = "https://example-resource.openai.azure.com/openai/deployments/claims-agent-deployment/chat/completions?api-version=2024-02-15-preview"
        )

        val result = AzureOpenAIProvider.validateConfig(config)

        assertTrue(result.valid)
        assertTrue(result.warnings.none { it.contains("apiVersion") })
    }

    @Test
    fun `test validate v1 responses endpoint does not require api version`() {
        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "gpt-4.1-mini",
            endpoint = "https://example-resource.services.ai.azure.com/openai/v1/responses"
        )

        val result = AzureOpenAIProvider.validateConfig(config)

        assertTrue(result.valid)
        assertTrue(result.warnings.none { it.contains("apiVersion") })
    }

    @Test
    fun `test get provider ID`() {
        val provider = AzureOpenAIProvider(
            config = AIProviderConfigDto(
                providerId = "azure-openai",
                modelName = "claims-agent-deployment",
                endpoint = "https://example-resource.openai.azure.com"
            ),
            credentialVault = credentialVault,
            userId = "user"
        )

        assertEquals("azure-openai", provider.getProviderId())
        assertNotNull(provider.getMetadata())
    }

    @Test
    fun `test execute uses responses endpoint without appending chat completions path`() {
        val endpoint = "https://example-resource.services.ai.azure.com/openai/v1/responses"
        val restTemplate = mock(RestTemplate::class.java)
        `when`(credentialVault.resolveCredentialRef("\$AZURE_OPENAI_API_KEY", "user"))
            .thenReturn("fake-key")
        `when`(
            restTemplate.postForObject(
                eq(endpoint),
                any(HttpEntity::class.java),
                eq(String::class.java)
            )
        ).thenReturn(
            """
            {
              "output_text": "approved",
              "usage": {
                "input_tokens": 10,
                "output_tokens": 3,
                "total_tokens": 13
              },
              "status": "completed"
            }
            """.trimIndent()
        )

        val config = AIProviderConfigDto(
            providerId = "azure-openai",
            modelName = "gpt-4.1-mini",
            endpoint = endpoint,
            credentialRefName = "\$AZURE_OPENAI_API_KEY"
        )
        val provider = AzureOpenAIProvider(
            config = config,
            credentialVault = credentialVault,
            userId = "user",
            restTemplate = restTemplate
        )

        val result = provider.execute(
            com.easy.bpm.ai.dto.AIExecutionRequestDto(
                promptTemplate = "Decide: {{claim}}",
                variables = mapOf("claim" to "refund"),
                providerConfig = config
            )
        )

        assertTrue(result.success)
        assertEquals("approved", result.responseText)
        assertEquals(13, result.tokensUsed)
        verify(restTemplate).postForObject(
            eq(endpoint),
            any(HttpEntity::class.java),
            eq(String::class.java)
        )
    }
}
