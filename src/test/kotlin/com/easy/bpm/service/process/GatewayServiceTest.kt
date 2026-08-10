package com.easy.bpm.service.process

import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*

class GatewayServiceTest : FunSpec() {
    init {
    val mockProcessVariableRepository = mockk<ProcessVariableRepository>()
    val objectMapper = ObjectMapper()

    val gatewayService = GatewayService(mockProcessVariableRepository, objectMapper)

    beforeEach {
        clearAllMocks()
    }

    context("getNextNodes - Exclusive Gateway") {
        test("should return next node for exclusive gateway") {
            // Arrange - simplified test, focusing on basic gateway navigation
            val definition = objectMapper.readTree("""
                {
                    "flows": []
                }
            """.trimIndent())

            val gatewayNode = objectMapper.readTree("""
                {
                    "id": "gateway-1",
                    "type": "ExclusiveGateway",
                    "next": ["task-1"]
                }
            """.trimIndent())

            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )

            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("gateway-1")
            )

            // Act - test basic next node retrieval without condition evaluation
            val result = gatewayService.getNextNodes(gatewayNode, definition, instance)

            // Assert
            result shouldHaveSize 1
            result shouldContain "task-1"
        }

        test("should throw when exclusive gateway has no matching route") {
            val definition = objectMapper.readTree("""
                {
                    "nodes": [
                        { "id": "gateway-1", "type": "ExclusiveGateway" },
                        { "id": "approve", "type": "UserTask" },
                        { "id": "reject", "type": "UserTask" }
                    ],
                    "flows": [
                        { "from": "gateway-1", "to": "approve", "condition": "${'$'}{routingDecision} == 'APPROVE'" },
                        { "from": "gateway-1", "to": "reject", "condition": "${'$'}{routingDecision} == 'REJECT'" }
                    ]
                }
            """.trimIndent())

            val gatewayNode = definition.get("nodes").first { it.get("id").asText() == "gateway-1" }
            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )
            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("gateway-1")
            )

            every {
                mockProcessVariableRepository.findByProcessInstanceIdAndName(100, "routingDecision")
            } returns ProcessVariable(processInstanceId = 100, name = "routingDecision", value = TextNode("NEEDS_REVIEW"))

            val error = shouldThrow<GatewayRoutingException> {
                gatewayService.getNextNodes(gatewayNode, definition, instance)
            }

            error.nodeId shouldBe "gateway-1"
        }

        test("should route using nested field from JSON string process variable") {
            val definition = objectMapper.readTree("""
                {
                    "nodes": [
                        { "id": "gateway-1", "type": "ExclusiveGateway" },
                        { "id": "human-review", "type": "UserTask" },
                        { "id": "end", "type": "EndEvent" }
                    ],
                    "flows": [
                        { "from": "gateway-1", "to": "human-review", "condition": "${'$'}{agentRecommendation.analysisStatus} == 'NEEDS_REVIEW'" },
                        { "from": "gateway-1", "to": "end", "condition": "${'$'}{agentRecommendation.analysisStatus} == 'APPROVED'" }
                    ]
                }
            """.trimIndent())

            val gatewayNode = definition.get("nodes").first { it.get("id").asText() == "gateway-1" }
            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )
            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("gateway-1")
            )

            every {
                mockProcessVariableRepository.findByProcessInstanceIdAndName(100, "agentRecommendation")
            } returns ProcessVariable(
                processInstanceId = 100,
                name = "agentRecommendation",
                value = TextNode("""{"analysisStatus":"NEEDS_REVIEW","riskLevel":"medio"}""")
            )

            val result = gatewayService.getNextNodes(gatewayNode, definition, instance)

            result shouldBe listOf("human-review")
        }
    }

    context("getNextNodes - Parallel Gateway (split)") {
        test("should return all outgoing paths for parallel split") {
            // Arrange
            val definition = objectMapper.readTree("""
                {
                    "flows": []
                }
            """.trimIndent())

            val gatewayNode = objectMapper.readTree("""
                {
                    "id": "gateway-1",
                    "type": "ParallelGateway",
                    "next": ["task-1", "task-2", "task-3"]
                }
            """.trimIndent())

            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )

            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("gateway-1")
            )

            // Act
            val result = gatewayService.getNextNodes(gatewayNode, definition, instance)

            // Assert
            result shouldHaveSize 3
            result shouldContain "task-1"
            result shouldContain "task-2"
            result shouldContain "task-3"
        }
    }

    context("getNextNodes - Standard node") {
        test("should return next nodes for standard node") {
            // Arrange
            val definition = objectMapper.readTree("""
                {
                    "flows": []
                }
            """.trimIndent())

            val node = objectMapper.readTree("""
                {
                    "id": "task-1",
                    "type": "UserTask",
                    "next": ["task-2"]
                }
            """.trimIndent())

            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )

            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("task-1")
            )

            // Act
            val result = gatewayService.getNextNodes(node, definition, instance)

            // Assert
            result shouldHaveSize 1
            result shouldContain "task-2"
        }

        test("should return empty list when no next nodes") {
            // Arrange
            val definition = objectMapper.readTree("""
                {
                    "flows": []
                }
            """.trimIndent())

            val node = objectMapper.readTree("""
                {
                    "id": "task-1",
                    "type": "UserTask"
                }
            """.trimIndent())

            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "test-process",
                definitionJson = definition.toString(),
                version = 1
            )

            val instance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("task-1")
            )

            // Act
            val result = gatewayService.getNextNodes(node, definition, instance)

            // Assert
            result.shouldBeEmpty()
        }
    }
    }
}
