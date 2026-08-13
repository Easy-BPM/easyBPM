package com.easy.bpm.controller

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.message.MessageEventInbox
import com.easy.bpm.enum.MessageEventInboxStatus
import com.easy.bpm.controller.data.AssignProcessVariablesRequest
import com.easy.bpm.service.message.ExternalMessageAcceptance
import com.easy.bpm.service.message.MessageEventInboxService
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.process.ProcessService
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
import org.springframework.http.HttpStatus

class ProcessControllerTest : FunSpec() {
    init {
    val mockProcessService = mockk<ProcessService>()
    val mockMessageEventInboxService = mockk<MessageEventInboxService>()
    val mockTimelineService = mockk<ProcessInstanceTimelineService>()
    val objectMapper = ObjectMapper()

    val processController = ProcessController(mockProcessService, mockMessageEventInboxService, mockTimelineService, objectMapper)

    beforeEach {
        clearAllMocks()
    }

    context("deploy") {
        test("should deploy process definition successfully") {
            // Arrange
            val processXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="my-process" name="my-process" isExecutable="true"/>
                </bpmn:definitions>
            """.trimIndent()

            val expectedDefinition = ProcessDefinition(
                id = 1,
                processName = "my-process",
                definitionJson = processXml,
                version = 1
            )

            every { mockProcessService.deployProcess(processXml) } returns expectedDefinition

            // Act
            val result = processController.deploy(processXml)

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.processName shouldBe "my-process"
            result.version shouldBe 1
            verify { mockProcessService.deployProcess(processXml) }
        }

        test("should propagate exception from service") {
            // Arrange
            val invalidXml = "<bpmn:definitions/>"

            every { mockProcessService.deployProcess(invalidXml) } throws IllegalArgumentException("Invalid process definition")

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.deploy(invalidXml)
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
            result.statusCode shouldBe HttpStatus.OK
            val body = result.body as ProcessInstance
            body.id shouldBe 100
            body.processDefinition.id shouldBe processDefinitionId
            verify { mockProcessService.startProcessInstance(processKey) }
        }

        test("should return not found when process definition does not exist") {
            // Arrange
            val processKey = "unknown"
            every { mockProcessService.startProcessInstance(processKey) } throws IllegalArgumentException("Process definition not found")

            // Act
            val result = processController.startInstance(processKey)

            // Assert
            result.statusCode shouldBe HttpStatus.NOT_FOUND
            @Suppress("UNCHECKED_CAST")
            val body = result.body as Map<String, Any>
            body["status"] shouldBe "error"
            body["message"] shouldBe "Process definition not found"
            body["processId"] shouldBe processKey
        }

        test("should explain message start processes cannot use the regular start endpoint") {
            // Arrange
            val processKey = "qa_message_timer_events"
            every { mockProcessService.startProcessInstance(processKey) } throws IllegalArgumentException("StartEvent not found")

            // Act
            val result = processController.startInstance(processKey)

            // Assert
            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            @Suppress("UNCHECKED_CAST")
            val body = result.body as Map<String, Any>
            body["status"] shouldBe "error"
            body["message"] shouldBe "Process '$processKey' does not have a regular StartEvent. It must be started by sending its MessageStartEvent payload to POST /processes/messages."
            body["messageEndpoint"] shouldBe "/processes/messages"
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

    context("assignProcessVariables") {
        test("should return conflict when process instance is completed") {
            // Arrange
            val instanceId = 100L
            val request = AssignProcessVariablesRequest(
                variables = mapOf("approved" to true)
            )
            every {
                mockProcessService.assignProcessVariables(instanceId, request.variables)
            } throws IllegalStateException("Cannot assign variables to a completed process instance")

            // Act
            val response = processController.assignProcessVariables(instanceId, request)

            // Assert
            response.statusCode.value() shouldBe 409
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

            val inboxMessage = MessageEventInbox(
                messageId = "msg-123",
                messageName = messageName,
                correlationKey = correlationKey,
                payload = variables,
                status = MessageEventInboxStatus.PROCESSED
            )

            every {
                mockMessageEventInboxService.acceptExternalMessage("msg-123", messageName, correlationKey, variables)
            } returns ExternalMessageAcceptance(inboxMessage, duplicate = false)

            // Act
            val result = processController.sendMessage("msg-123", request)

            // Assert
            result["status"] shouldBe "success"
            result["messageId"] shouldBe "msg-123"
            result["messageName"] shouldBe messageName
            result["correlationKey"] shouldBe correlationKey
            result["correlated"] shouldBe true
            result["duplicate"] shouldBe false
            verify { mockMessageEventInboxService.acceptExternalMessage("msg-123", messageName, correlationKey, variables) }
        }

        test("should throw exception when messageName is missing") {
            // Arrange
            val request = mapOf(
                "correlationKey" to "order-123",
                "variables" to mapOf("amount" to 100.0)
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processController.sendMessage(null, request)
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
                processController.sendMessage(null, request)
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

            val inboxMessage = MessageEventInbox(
                messageId = "body-msg-123",
                messageName = messageName,
                correlationKey = correlationKey,
                payload = null,
                status = MessageEventInboxStatus.PROCESSED
            )

            every {
                mockMessageEventInboxService.acceptExternalMessage("body-msg-123", messageName, correlationKey, null)
            } returns ExternalMessageAcceptance(inboxMessage, duplicate = false)

            // Act
            val result = processController.sendMessage(null, request + ("messageId" to "body-msg-123"))

            // Assert
            result["status"] shouldBe "success"
            verify { mockMessageEventInboxService.acceptExternalMessage("body-msg-123", messageName, correlationKey, null) }
        }

        test("should return unmatched when no process is waiting") {
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val request = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey
            )
            val inboxMessage = MessageEventInbox(
                messageId = "msg-unmatched",
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageEventInboxStatus.UNMATCHED,
                errorMessage = "No waiting subscription"
            )

            every {
                mockMessageEventInboxService.acceptExternalMessage("msg-unmatched", messageName, correlationKey, null)
            } returns ExternalMessageAcceptance(inboxMessage, duplicate = false)

            val result = processController.sendMessage("msg-unmatched", request)

            result["status"] shouldBe "unmatched"
            result["correlated"] shouldBe false
            result["duplicate"] shouldBe false
        }

        test("should not reprocess duplicate message id") {
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val request = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey
            )
            val inboxMessage = MessageEventInbox(
                messageId = "msg-duplicate",
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageEventInboxStatus.PROCESSED
            )

            every {
                mockMessageEventInboxService.acceptExternalMessage("msg-duplicate", messageName, correlationKey, null)
            } returns ExternalMessageAcceptance(inboxMessage, duplicate = true)

            val result = processController.sendMessage("msg-duplicate", request)

            result["status"] shouldBe "success"
            result["duplicate"] shouldBe true
        }
    }
    }
}
