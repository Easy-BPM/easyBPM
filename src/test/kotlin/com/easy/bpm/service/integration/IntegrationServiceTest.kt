package com.easy.bpm.service.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI
import kotlin.test.assertEquals

class IntegrationServiceTest {
    private val restTemplate = mockk<RestTemplate>()
    private val processVariableRepository = mockk<ProcessVariableRepository>()
    private val objectMapper = ObjectMapper()
    private val service = IntegrationService(restTemplate, processVariableRepository, objectMapper)

    @Test
    fun `resolves process variables in url before calling api`() {
        val instance = processInstance()
        val savedVariable = slot<ProcessVariable>()
        val config = objectMapper.readTree(
            """
            {
              "url": "http://host.docker.internal:9095/customers/{{customerId}}",
              "method": "GET"
            }
            """.trimIndent()
        )

        every { processVariableRepository.findByProcessInstanceId(instance.id) } returns listOf(
            ProcessVariable(
                processInstanceId = instance.id,
                name = "customerId",
                value = objectMapper.valueToTree("CUST-1001")
            )
        )
        every {
            restTemplate.exchange(
                URI.create("http://host.docker.internal:9095/customers/CUST-1001"),
                HttpMethod.GET,
                any<HttpEntity<Any?>>(),
                Map::class.java
            )
        } returns ResponseEntity.ok(mapOf("customerTier" to "PLATINUM"))
        every { processVariableRepository.findByProcessInstanceIdAndName(instance.id, "customerTier") } returns null
        every { processVariableRepository.save(capture(savedVariable)) } answers { firstArg() }

        val outputs = service.executeIntegration(instance, "task-3", config)

        assertEquals("PLATINUM", outputs["customerTier"])
        assertEquals("customerTier", savedVariable.captured.name)
        assertEquals("PLATINUM", savedVariable.captured.value.asText())
        verify {
            restTemplate.exchange(
                URI.create("http://host.docker.internal:9095/customers/CUST-1001"),
                HttpMethod.GET,
                any<HttpEntity<Any?>>(),
                Map::class.java
            )
        }
    }

    @Test
    fun `throws clear error when url references a missing process variable`() {
        val instance = processInstance()
        val config = objectMapper.readTree(
            """
            {
              "url": "http://host.docker.internal:9095/customers/{{customerId}}",
              "method": "GET"
            }
            """.trimIndent()
        )

        every { processVariableRepository.findByProcessInstanceId(instance.id) } returns emptyList()

        val error = assertThrows<IllegalArgumentException> {
            service.executeIntegration(instance, "task-3", config)
        }

        assertTrue(error.message!!.contains("task-3"))
        assertTrue(error.message!!.contains("customerId"))
    }

    private fun processInstance() =
        ProcessInstance(
            id = 3,
            processDefinition = ProcessDefinition(
                id = 1,
                key = "demo",
                processName = "Demo",
                definitionJson = "{}"
            ),
            status = ProcessStatus.ACTIVE
        )
}
