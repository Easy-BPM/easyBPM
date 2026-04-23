package com.easy.bpm.controller

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.ProcessService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ProcessControllerTest : FunSpec({
    val mockProcessService = mockk<ProcessService>()
    val objectMapper = ObjectMapper()

    val processController = ProcessController(mockProcessService, objectMapper)

    beforeEach {
        clearAllMocks()
    }

    context("deploy") {
        test("should deploy process definition successfully") {
            // Arrange
            val processJson = objectMapper.readTree("""
                {
                    "processId": "my-process",
                    "version": 1,
                    "nodes": []
                }
            """.trimIndent())

            val expectedDefinition = ProcessDefinition(
                id = 1,
                processName = "my-process",
                definitionJson = processJson.toString(),
                version = 1
            )

            every { mockProcessService.deployProcess(processJson) } returns expectedDefinition

            // Act
            val result = processController.deploy(processJson)

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.processName shouldBe "my-process"
            result.version shouldBe 1
            verify { mockProcessService.deployProcess(processJson) }
        }

        test("should propagate exception from service") {
            // Arrange
            val invalidJson = objectMapper.readTree("""
                {
                    "version": 1
                }
            """.trimIndent())

            every { mockProcessService.deployProcess(invalidJson) } throws IllegalArgumentException("Invalid process definition")

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.deploy(invalidJson)
            }
        }
    }

    context("startInstance") {
        test("should start process instance successfully") {
            // Arrange
            val processKey = "my-process"
            val processDefinitionId = 1L
            val processDefinition = ProcessDefinition(
                id = processDefinitionId,
                processName = "my-process",
                definitionJson = "{}",
                version = 1
            )
            val expectedInstance = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("start-1")
            )

            every { mockProcessService.startProcessInstance(processKey) } returns expectedInstance

            // Act
            val result = processController.startInstance(processKey)

            // Assert
            result shouldNotBe null
            result.id shouldBe 100
            result.processDefinition.id shouldBe processDefinitionId
            verify { mockProcessService.startProcessInstance(processKey) }
        }

        test("should throw exception when process definition not found") {
            // Arrange
            val processKey = "unknown"
            every { mockProcessService.startProcessInstance(processKey) } throws IllegalArgumentException("Process definition not found")

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.startInstance(processKey)
            }
        }
    }

    context("getProcessInstances") {
        test("should retrieve paginated process instances") {
            // Arrange
            val processDefinition = ProcessDefinition(
                id = 1,
                processName = "my-process",
                definitionJson = "{}",
                version = 1
            )
            val instance1 = ProcessInstance(
                id = 100,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.ACTIVE,
                currentNode = listOf("node-1")
            )
            val instance2 = ProcessInstance(
                id = 101,
                processDefinition = processDefinition,
                status = com.easy.bpm.enum.ProcessStatus.COMPLETED,
                currentNode = emptyList()
            )

            val page: Page<ProcessInstance> = PageImpl(
                listOf(instance1, instance2),
                PageRequest.of(0, 10),
                2
            )
            every { mockProcessService.getProcessInstances(any()) } returns page

            // Act
            val result = processController.getProcessInstances(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.totalElements shouldBe 2
            result.content[0].id shouldBe 100
            result.content[1].id shouldBe 101
        }

        test("should return empty page when no instances exist") {
            // Arrange
            val emptyPage: Page<ProcessInstance> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            every { mockProcessService.getProcessInstances(any()) } returns emptyPage

            // Act
            val result = processController.getProcessInstances(PageRequest.of(0, 10))

            // Assert
            result.content.size shouldBe 0
            result.totalElements shouldBe 0
        }
    }

    context("getLatestProcesses") {
        test("should retrieve latest process definitions") {
            // Arrange
            val definition1 = ProcessDefinition(
                id = 1,
                processName = "process-1",
                definitionJson = "{}",
                version = 2
            )
            val definition2 = ProcessDefinition(
                id = 2,
                processName = "process-2",
                definitionJson = "{}",
                version = 1
            )

            val page: Page<ProcessDefinition> = PageImpl(
                listOf(definition1, definition2),
                PageRequest.of(0, 10),
                2
            )
            every { mockProcessService.getLatestProcessDefinitions(any()) } returns page

            // Act
            val result = processController.getLatestProcesses(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.content[0].version shouldBe 2
            result.content[1].version shouldBe 1
        }
    }

    context("getProcessDefinitionById") {
        test("should return process definition when found") {
            // Arrange
            val definitionId = 5L
            val definition = ProcessDefinition(
                id = definitionId,
                key = "expense",
                processName = "Expense Approval",
                definitionJson = "{}",
                version = 2
            )
            every { mockProcessService.getProcessDefinitionById(definitionId) } returns definition

            // Act
            val response = processController.getProcessDefinitionById(definitionId)

            // Assert
            response.statusCode.value() shouldBe 200
            response.body shouldBe definition
        }

        test("should return 404 when process definition is not found") {
            // Arrange
            val definitionId = 404L
            every { mockProcessService.getProcessDefinitionById(definitionId) } returns null

            // Act
            val response = processController.getProcessDefinitionById(definitionId)

            // Assert
            response.statusCode.value() shouldBe 404
            response.body shouldBe null
        }
    }

    context("sendMessage") {
        test("should send message successfully") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val variables = mapOf(
                "amount" to 100.0,
                "currency" to "USD"
            )

            val request = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey,
                "variables" to variables
            )

            every { mockProcessService.handleMessageReceived(messageName, correlationKey, variables) } just runs

            // Act
            val result = processController.sendMessage(request)

            // Assert
            result["status"] shouldBe "success"
            result["messageName"] shouldBe messageName
            result["correlationKey"] shouldBe correlationKey
            verify { mockProcessService.handleMessageReceived(messageName, correlationKey, variables) }
        }

        test("should throw exception when messageName is missing") {
            // Arrange
            val request = mapOf(
                "correlationKey" to "order-123",
                "variables" to mapOf("amount" to 100.0)
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.sendMessage(request)
            }
        }

        test("should throw exception when correlationKey is missing") {
            // Arrange
            val request = mapOf(
                "messageName" to "PaymentReceived",
                "variables" to mapOf("amount" to 100.0)
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.sendMessage(request)
            }
        }

        test("should handle missing variables") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"

            val request = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey
            )

            every { mockProcessService.handleMessageReceived(messageName, correlationKey, null) } just runs

            // Act
            val result = processController.sendMessage(request)

            // Assert
            result["status"] shouldBe "success"
            verify { mockProcessService.handleMessageReceived(messageName, correlationKey, null) }
        }
    }
})

