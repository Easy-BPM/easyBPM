package com.easy.bpm.handler

import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.ai.service.CredentialVault
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.IncidentService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AITaskHandlerTest {

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
    fun `executes AI task and returns configured output variable`() {
        val provider = mockk<AIProvider>()
        val requestSlot = slot<com.easy.bpm.ai.dto.AIExecutionRequestDto>()
        every { aiProviderFactory.createProvider("openai", any(), "ai-task-executor") } returns provider
        every { provider.execute(capture(requestSlot)) } returns AIExecutionResponseDto(
            responseText = "This is a summary.",
            success = true,
            tokensUsed = 42
        )

        val result = handler.executeAITask(
            instanceId = 10L,
            node = aiNode(),
            inputVariables = mapOf("text" to "This is a long document.")
        )

        assertEquals("This is a summary.", result["summary"])
        assertEquals("Summarize: This is a long document.", requestSlot.captured.userPrompt)
        verify(exactly = 1) { provider.execute(any()) }
    }

    @Test
    fun `serializes non scalar input variables during prompt substitution`() {
        val provider = mockk<AIProvider>()
        val requestSlot = slot<com.easy.bpm.ai.dto.AIExecutionRequestDto>()
        every { aiProviderFactory.createProvider("openai", any(), "ai-task-executor") } returns provider
        every { provider.execute(capture(requestSlot)) } returns AIExecutionResponseDto(
            responseText = """{"ok":true}""",
            success = true
        )

        handler.executeAITask(
            instanceId = 10L,
            node = aiNode(promptTemplate = "Analyze {{payload}}"),
            inputVariables = mapOf("payload" to mapOf("customerId" to "CUST-1", "amount" to 15))
        )

        assertEquals("""Analyze {"customerId":"CUST-1","amount":15}""", requestSlot.captured.userPrompt)
    }

    @Test
    fun `throws INVALID_CONFIG when required config is missing`() {
        val node = objectMapper.readTree(
            """
            {
              "id": "ai-task-1",
              "type": "AITask",
              "properties": {
                "providerId": "openai"
              }
            }
            """.trimIndent()
        )

        val error = assertThrows<AITaskExecutionException> {
            handler.executeAITask(10L, node, emptyMap())
        }

        assertEquals("INVALID_CONFIG", error.errorCode)
    }

    private fun aiNode(
        promptTemplate: String = "Summarize: {{text}}",
        tuningParams: String = ""
    ) = objectMapper.readTree(
        """
        {
          "id": "ai-task-1",
          "type": "AITask",
          "properties": {
            "providerId": "openai",
            "modelName": "gpt-3.5-turbo",
            "promptTemplate": "$promptTemplate",
            "outputVariable": "summary"
            ${if (tuningParams.isBlank()) "" else "," + tuningParams}
          }
        }
        """.trimIndent()
    )
}
