package com.easy.bpm.handler

import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.agent.AgentProcessDefinition
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.easy.bpm.repository.agent.AgentProcessExecutionRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentProcessCallHandlerTest {
    private val definitionRepository = mockk<AgentProcessDefinitionRepository>()
    private val executionRepository = mockk<AgentProcessExecutionRepository>()
    private val variableRepository = mockk<ProcessVariableRepository>(relaxed = true)
    private val providerFactory = mockk<AIProviderFactory>()
    private val objectMapper = ObjectMapper()
    private val invocationExecutor = AgentProcessInvocationExecutor()
    private val handler = AgentProcessCallHandler(
        definitionRepository,
        executionRepository,
        variableRepository,
        objectMapper,
        providerFactory,
        invocationExecutor
    )

    @AfterEach
    fun tearDown() {
        invocationExecutor.shutdown()
    }

    @Test
    fun `always executes latest agent definition and ignores a pinned invocation version`() {
        val latestDefinition = AgentProcessDefinition(
            id = 55,
            key = "weather-agent",
            processName = "Weather Agent",
            version = 7,
            definitionJson =
                """
                {
                  "goal": "Recommend activities",
                  "provider": {
                    "providerId": "ollama",
                    "modelName": "llama3.2",
                    "promptTemplate": "Goal: {{goal}}; Inputs: {{inputs}}"
                  }
                }
                """.trimIndent()
        )
        val provider = mockk<AIProvider>()
        val savedExecution = slot<com.easy.bpm.model.agent.AgentProcessExecution>()

        every { definitionRepository.findTopByKeyOrderByVersionDesc("weather-agent") } returns latestDefinition
        every { executionRepository.save(capture(savedExecution)) } answers { firstArg() }
        every { variableRepository.findByProcessInstanceIdAndName(any(), any()) } returns null
        every { variableRepository.save(any()) } answers { firstArg() }
        every { providerFactory.createProvider("ollama", any(), "agent-process-runtime") } returns provider
        every { provider.execute(any()) } returns AIExecutionResponseDto("Use a park", success = true)

        val result = handler.execute(
            processInstance(),
            objectMapper.readTree(
                """
                {
                  "id": "consult-weather",
                  "type": "AgentProcessCall",
                  "config": {
                    "agentProcessKey": "weather-agent",
                    "agentProcessVersion": 1
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(55, result.agentProcessDefinitionId)
        assertEquals(7, objectMapper.readTree(result.decisionTrace).get("agentProcessVersion").asInt())
        verify(exactly = 1) { definitionRepository.findTopByKeyOrderByVersionDesc("weather-agent") }
        verify(exactly = 1) { provider.execute(any()) }
    }

    @Test
    fun `resolves configurable timeout units and legacy days`() {
        val seconds = handler.resolveTimeout(objectMapper.readTree("""{"timeoutValue":30,"timeoutUnit":"SECONDS"}"""))
        val minutes = handler.resolveTimeout(objectMapper.readTree("""{"timeoutValue":5,"timeoutUnit":"MINUTES"}"""))
        val legacy = handler.resolveTimeout(objectMapper.readTree("""{"timeoutDays":2}"""))

        assertEquals(Duration.ofSeconds(30), seconds?.duration)
        assertEquals(Duration.ofMinutes(5), minutes?.duration)
        assertEquals(Duration.ofDays(2), legacy?.duration)
    }

    @Test
    fun `rejects invalid timeout values`() {
        val error = assertThrows<IllegalArgumentException> {
            handler.resolveTimeout(objectMapper.readTree("""{"timeoutValue":0,"timeoutUnit":"MINUTES"}"""))
        }

        assertTrue(error.message.orEmpty().contains("greater than zero"))
    }

    private fun processInstance() = ProcessInstance(
        id = 91,
        processDefinition = ProcessDefinition(
            id = 12,
            key = "activity-demo",
            processName = "Activity Demo",
            definitionJson = "{}"
        ),
        status = ProcessStatus.ACTIVE
    )
}
