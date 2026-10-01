package com.easy.bpm.service.agent

import com.easy.bpm.ai.dto.AIExecutionRequestDto
import com.easy.bpm.ai.dto.AIExecutionResponseDto
import com.easy.bpm.ai.factory.AIProviderFactory
import com.easy.bpm.ai.provider.AIProvider
import com.easy.bpm.ai.service.CredentialVault
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class AgentProcessSimulationServiceTest : FunSpec({
    val objectMapper = ObjectMapper()
    val agentProcessService = mockk<AgentProcessService>()
    val providerFactory = mockk<AIProviderFactory>()
    val credentialVault = mockk<CredentialVault>(relaxed = true)
    val restTemplate = mockk<RestTemplate>()
    val provider = mockk<AIProvider>()
    val service = AgentProcessSimulationService(
        agentProcessService,
        objectMapper,
        providerFactory,
        credentialVault,
        restTemplate
    )

    test("should execute API tools and include verified output in the provider prompt") {
        val definition = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "weather-agent",
              "goal": "Recommend activities for the current weather.",
              "instructions": "Use the weather tool.",
              "constraints": ["Do not invent weather data."],
              "availableTools": [{
                "id": "weather",
                "name": "Weather API",
                "type": "api-call",
                "url": "https://203.0.113.10/weather",
                "method": "GET",
                "auth": { "type": "none" }
              }],
              "provider": {
                "providerId": "ollama",
                "modelName": "llama3.2",
                "endpoint": "http://host.docker.internal:11434"
              }
            }
            """.trimIndent()
        )
        every { agentProcessService.validateDraft(definition) } returns definition
        every {
            restTemplate.exchange(
                URI.create("https://203.0.113.10/weather"),
                HttpMethod.GET,
                any<HttpEntity<Any?>>(),
                String::class.java
            )
        } returns ResponseEntity.ok("""{"current":{"temperature_2m":21.3,"wind_speed_10m":5.5}}""")
        every { providerFactory.createProvider("ollama", any(), "agent-process-simulation") } returns provider
        val executionRequest = slot<AIExecutionRequestDto>()
        every { provider.execute(capture(executionRequest)) } returns AIExecutionResponseDto(
            responseText = "Three outdoor activities are recommended.",
            tokensUsed = 42,
            executionDurationMs = 125,
            success = true
        )

        val result = service.simulate(AgentProcessSimulationRequest(definition, objectMapper.createObjectNode()))

        result.success shouldBe true
        result.responseText shouldContain "outdoor activities"
        result.toolResults.single().status shouldBe "COMPLETED"
        result.toolResults.single().response?.at("/current/temperature_2m")?.asDouble() shouldBe 21.3
        executionRequest.captured.userPrompt.orEmpty() shouldContain "21.3"
        executionRequest.captured.userPrompt.orEmpty() shouldContain "Verified tool execution results"
    }

    test("should block private network API tool URLs") {
        clearMocks(providerFactory, answers = false, recordedCalls = true)
        val definition = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "unsafe-agent",
              "goal": "Read internal data.",
              "availableTools": [{
                "id": "internal",
                "name": "Internal API",
                "type": "api-call",
                "url": "http://127.0.0.1:8080/private",
                "method": "GET",
                "auth": { "type": "none" }
              }],
              "provider": {
                "providerId": "ollama",
                "modelName": "llama3.2"
              }
            }
            """.trimIndent()
        )
        every { agentProcessService.validateDraft(definition) } returns definition

        val result = service.simulate(AgentProcessSimulationRequest(definition, objectMapper.createObjectNode()))

        result.success shouldBe false
        result.errorCode shouldBe "TOOL_EXECUTION_FAILED"
        result.toolResults.single().error.orEmpty() shouldContain "local or private network"
        verify(exactly = 0) { providerFactory.createProvider(any(), any(), any()) }
    }
})
