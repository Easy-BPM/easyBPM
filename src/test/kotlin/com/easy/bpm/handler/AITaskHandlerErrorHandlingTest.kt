package com.easy.bpm.handler

import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.ai.service.CredentialVault
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.incident.IncidentService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AITaskHandlerErrorHandlingTest {

    private lateinit var aiProviderFactory: AIProviderFactory
    private lateinit var credentialVault: CredentialVault
    private lateinit var processVariableRepository: ProcessVariableRepository
    private lateinit var incidentService: IncidentService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: AITaskHandler

    @BeforeEach
    fun setup() {
        aiProviderFactory = mockk()
        credentialVault = mockk(relaxed = true)
        processVariableRepository = mockk(relaxed = true)
        incidentService = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        handler = AITaskHandler(
            aiProviderFactory = aiProviderFactory,
            credentialVault = credentialVault,
            processVariableRepository = processVariableRepository,
            incidentService = incidentService,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `retries TIMEOUT and returns successful retry response`() {
        val provider = mockk<AIProvider>()
        every { aiProviderFactory.createProvider("openai", any(), "ai-task-executor") } returns provider
        every { provider.execute(any()) } returnsMany listOf(
            AIExecutionResponseDto(responseText = "", success = false, errorCode = "TIMEOUT", errorMessage = "Timed out"),
            AIExecutionResponseDto(responseText = "Recovered", success = true)
        )

        val result = handler.executeAITask(
            instanceId = 10L,
            node = aiNode(retryCount = 1),
            inputVariables = mapOf("text" to "hello")
        )

        assertEquals("Recovered", result["summary"])
        verify(exactly = 2) { provider.execute(any()) }
    }

    @Test
    fun `does not retry AUTH_ERROR`() {
        val provider = mockk<AIProvider>()
        every { aiProviderFactory.createProvider("openai", any(), "ai-task-executor") } returns provider
        every { provider.execute(any()) } returns AIExecutionResponseDto(
            responseText = "",
            success = false,
            errorCode = "AUTH_ERROR",
            errorMessage = "Invalid API key"
        )

        val error = assertThrows<AITaskExecutionException> {
            handler.executeAITask(
                instanceId = 10L,
                node = aiNode(retryCount = 3),
                inputVariables = mapOf("text" to "hello")
            )
        }

        assertEquals("AUTH_ERROR", error.errorCode)
        verify(exactly = 1) { provider.execute(any()) }
    }

    @Test
    fun `throws retryable error after retries are exhausted`() {
        val provider = mockk<AIProvider>()
        every { aiProviderFactory.createProvider("openai", any(), "ai-task-executor") } returns provider
        every { provider.execute(any()) } returns AIExecutionResponseDto(
            responseText = "",
            success = false,
            errorCode = "TIMEOUT",
            errorMessage = "Timed out"
        )

        val error = assertThrows<AITaskExecutionException> {
            handler.executeAITask(
                instanceId = 10L,
                node = aiNode(retryCount = 2),
                inputVariables = mapOf("text" to "hello")
            )
        }

        assertEquals("TIMEOUT", error.errorCode)
        verify(exactly = 3) { provider.execute(any()) }
    }

    private fun aiNode(retryCount: Int) = objectMapper.readTree(
        """
        {
          "id": "ai-task-1",
          "type": "AITask",
          "properties": {
            "providerId": "openai",
            "modelName": "gpt-3.5-turbo",
            "promptTemplate": "Summarize: {{text}}",
            "outputVariable": "summary",
            "tuningParams": {
              "retryCount": $retryCount,
              "initialDelayMs": 1,
              "backoffMultiplier": 1.0
            }
          }
        }
        """.trimIndent()
    )
}
