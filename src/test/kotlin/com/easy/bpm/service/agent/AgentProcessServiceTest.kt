package com.easy.bpm.service.agent

import com.easy.bpm.model.agent.AgentProcessDefinition
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AgentProcessServiceTest : FunSpec({
    val repository = mockk<AgentProcessDefinitionRepository>()
    val service = AgentProcessService(repository)
    val objectMapper = ObjectMapper()

    test("should deploy agent process with provider configuration") {
        val json = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "customer-support-resolution",
              "processName": "Customer Support Resolution",
              "goal": "Resolve customer complaint.",
              "provider": {
                "providerId": "openai",
                "modelName": "gpt-4o-mini",
                "credentialRef": "${'$'}OPENAI_API_KEY"
              },
              "steps": []
            }
            """.trimIndent()
        )

        every { repository.findTopByKeyOrderByVersionDesc("customer-support-resolution") } returns null
        every { repository.save(any<AgentProcessDefinition>()) } answers { firstArg() }

        val deployed = service.deploy(json)

        deployed.key shouldBe "customer-support-resolution"
        deployed.version shouldBe 1
        deployed.definitionJson shouldBe json.toString()
        verify { repository.save(any<AgentProcessDefinition>()) }
    }

    test("should deploy agent process with structured API and code task tools") {
        val json = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "customer-support-resolution",
              "processName": "Customer Support Resolution",
              "goal": "Resolve customer complaint.",
              "provider": {
                "providerId": "openai",
                "modelName": "gpt-4o-mini"
              },
              "availableTools": [
                {
                  "id": "crm_lookup",
                  "name": "CRM Lookup",
                  "type": "api-call",
                  "description": "Read customer context from CRM.",
                  "url": "https://crm.example.com/customers/{{customerId}}",
                  "method": "GET",
                  "auth": {
                    "type": "bearer",
                    "ref": "CRM_API_TOKEN"
                  },
                  "inputSchema": {
                    "type": "object"
                  },
                  "outputSchema": {
                    "type": "object"
                  }
                },
                {
                  "id": "refund_policy",
                  "name": "Refund Policy",
                  "type": "code-task",
                  "description": "Evaluate refund policy in Java.",
                  "jarId": 12,
                  "className": "com.easy.bpm.policy.RefundPolicyService",
                  "methodName": "evaluateRefund",
                  "inputSchema": {
                    "type": "object"
                  },
                  "outputSchema": {
                    "type": "object"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        every { repository.findTopByKeyOrderByVersionDesc("customer-support-resolution") } returns null
        every { repository.save(any<AgentProcessDefinition>()) } answers { firstArg() }

        val deployed = service.deploy(json)

        deployed.key shouldBe "customer-support-resolution"
        deployed.definitionJson shouldBe json.toString()
    }

    test("should reject provider configuration without model name") {
        val json = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "bad-agent",
              "goal": "Resolve customer complaint.",
              "provider": {
                "providerId": "openai"
              }
            }
            """.trimIndent()
        )

        shouldThrow<IllegalArgumentException> {
            service.deploy(json)
        }
    }

    test("should reject structured agent tool without required runtime fields") {
        val json = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "bad-agent",
              "goal": "Resolve customer complaint.",
              "availableTools": [
                {
                  "name": "Broken Java Tool",
                  "type": "code-task",
                  "className": "com.easy.bpm.policy.RefundPolicyService"
                }
              ]
            }
            """.trimIndent()
        )

        shouldThrow<IllegalArgumentException> {
            service.deploy(json)
        }
    }
})
