package com.easy.bpm.messaging

import com.easy.bpm.service.ProcessService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.mockk.*

class RabbitListenerServiceTest : FunSpec() {
    init {
    val mockProcessService = mockk<ProcessService>()
    val rabbitListenerService = RabbitListenerService(mockProcessService)

    beforeEach {
        clearAllMocks()
    }

    context("onServiceTaskCompleted") {
        test("should handle service task completion with Long processInstanceId") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "service-task-1"
            val outputs = mapOf(
                "result" to "success",
                "data" to "some-value"
            )

            val message = mapOf(
                "processInstanceId" to processInstanceId,
                "nodeId" to nodeId,
                "outputs" to outputs as Any
            )

            every { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) } just runs

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) }
        }

        test("should handle service task completion with Int processInstanceId") {
            // Arrange
            val processInstanceIdInt = 100
            val processInstanceIdLong = 100L
            val nodeId = "service-task-1"
            val outputs = mapOf(
                "result" to "success"
            )

            val message = mapOf(
                "processInstanceId" to processInstanceIdInt,
                "nodeId" to nodeId,
                "outputs" to outputs as Any
            )

            every { mockProcessService.handleServiceTaskCompleted(processInstanceIdLong, nodeId, any()) } just runs

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify { mockProcessService.handleServiceTaskCompleted(processInstanceIdLong, nodeId, any()) }
        }

        test("should handle service task completion with String processInstanceId") {
            // Arrange
            val processInstanceIdString = "100"
            val processInstanceIdLong = 100L
            val nodeId = "service-task-1"
            val outputs = mapOf(
                "result" to "success"
            )

            val message = mapOf(
                "processInstanceId" to processInstanceIdString,
                "nodeId" to nodeId,
                "outputs" to outputs as Any
            )

            every { mockProcessService.handleServiceTaskCompleted(processInstanceIdLong, nodeId, any()) } just runs

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify { mockProcessService.handleServiceTaskCompleted(processInstanceIdLong, nodeId, any()) }
        }

        test("should skip processing when nodeId is missing") {
            // Arrange
            val message = mapOf(
                "processInstanceId" to 100L,
                "outputs" to mapOf("result" to "success") as Any
            )

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify(exactly = 0) { mockProcessService.handleServiceTaskCompleted(any(), any(), any()) }
        }

        test("should handle empty outputs") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "service-task-1"

            val message = mapOf(
                "processInstanceId" to processInstanceId,
                "nodeId" to nodeId
            )

            every { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) } just runs

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) }
        }

        test("should convert outputs map to string values") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "service-task-1"
            val outputs = mapOf(
                "status" to "completed",
                "code" to 200,
                "data" to mapOf("key" to "value")
            ) as Map<String, Any>

            val message = mapOf(
                "processInstanceId" to processInstanceId,
                "nodeId" to nodeId,
                "outputs" to outputs
            )

            every { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) } just runs

            // Act
            rabbitListenerService.onServiceTaskCompleted(message)

            // Assert
            verify { mockProcessService.handleServiceTaskCompleted(processInstanceId, nodeId, any()) }
        }
    }

    context("onServiceTaskFailed") {
        test("should handle service task failure from DLQ message") {
            // Arrange
            val processInstanceId = 200L
            val nodeId = "api-task-1"
            val dlqReason = "Connection refused"

            val message = mapOf(
                "processInstanceId" to processInstanceId,
                "nodeId" to nodeId,
                "dlqReason" to dlqReason
            )

            every { mockProcessService.handleServiceTaskFailed(processInstanceId, nodeId, dlqReason) } just runs

            // Act
            rabbitListenerService.onServiceTaskFailed(message)

            // Assert
            verify { mockProcessService.handleServiceTaskFailed(processInstanceId, nodeId, dlqReason) }
        }

        test("should skip processing when nodeId is missing") {
            // Arrange
            val message = mapOf(
                "processInstanceId" to 200L,
                "dlqReason" to "Connection refused"
            )

            // Act
            rabbitListenerService.onServiceTaskFailed(message)

            // Assert
            verify(exactly = 0) { mockProcessService.handleServiceTaskFailed(any(), any(), any()) }
        }
    }

    context("onMessageReceived") {
        test("should handle message received event") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val variables = mapOf(
                "amount" to 100.0,
                "currency" to "USD"
            )

            val message = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey,
                "variables" to variables as Any
            )

            every { mockProcessService.handleMessageReceived(messageName, correlationKey, variables) } just runs

            // Act
            rabbitListenerService.onMessageReceived(message)

            // Assert
            verify { mockProcessService.handleMessageReceived(messageName, correlationKey, variables) }
        }

        test("should skip processing when messageName is missing") {
            // Arrange
            val message = mapOf(
                "correlationKey" to "order-123",
                "variables" to mapOf("amount" to 100.0) as Any
            )

            // Act
            rabbitListenerService.onMessageReceived(message)

            // Assert
            verify(exactly = 0) { mockProcessService.handleMessageReceived(any(), any(), any()) }
        }

        test("should skip processing when correlationKey is missing") {
            // Arrange
            val message = mapOf(
                "messageName" to "PaymentReceived",
                "variables" to mapOf("amount" to 100.0) as Any
            )

            // Act
            rabbitListenerService.onMessageReceived(message)

            // Assert
            verify(exactly = 0) { mockProcessService.handleMessageReceived(any(), any(), any()) }
        }

        test("should handle message without variables") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"

            val message = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey
            )

            every { mockProcessService.handleMessageReceived(messageName, correlationKey, null) } just runs

            // Act
            rabbitListenerService.onMessageReceived(message)

            // Assert
            verify { mockProcessService.handleMessageReceived(messageName, correlationKey, null) }
        }

        test("should catch and handle IllegalArgumentException") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-999"
            val variables = mapOf("amount" to 100.0)

            val message = mapOf(
                "messageName" to messageName,
                "correlationKey" to correlationKey,
                "variables" to variables as Any
            )

            every {
                mockProcessService.handleMessageReceived(messageName, correlationKey, variables)
            } throws IllegalArgumentException("No subscription found")

            // Act - should not throw
            rabbitListenerService.onMessageReceived(message)

            // Assert
            verify { mockProcessService.handleMessageReceived(messageName, correlationKey, variables) }
        }
    }
    }
}
