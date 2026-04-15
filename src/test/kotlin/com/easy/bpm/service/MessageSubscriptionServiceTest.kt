package com.easy.bpm.service

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.model.message.MessageSubscription
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import java.time.LocalDateTime
import java.util.*

class MessageSubscriptionServiceTest : FunSpec({
    val mockMessageSubscriptionRepository = mockk<MessageSubscriptionRepository>()
    val messageSubscriptionService = MessageSubscriptionService(mockMessageSubscriptionRepository)

    beforeEach {
        clearAllMocks()
    }

    context("subscribeToMessage") {
        test("should create message subscription successfully") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "message-event-1"
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val timeoutAt = LocalDateTime.now().plusMinutes(5)

            val expectedSubscription = MessageSubscription(
                id = 1,
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = timeoutAt
            )

            every { mockMessageSubscriptionRepository.save(any()) } returns expectedSubscription

            // Act
            val result = messageSubscriptionService.subscribeToMessage(
                processInstanceId,
                nodeId,
                messageName,
                correlationKey,
                timeoutAt
            )

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.messageName shouldBe messageName
            result.correlationKey shouldBe correlationKey
            result.status shouldBe MessageSubscriptionStatus.AWAITING
            result.timeoutAt shouldBe timeoutAt
            verify { mockMessageSubscriptionRepository.save(any()) }
        }

        test("should create subscription without timeout") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "message-event-1"
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"

            val expectedSubscription = MessageSubscription(
                id = 1,
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = null
            )

            every { mockMessageSubscriptionRepository.save(any()) } returns expectedSubscription

            // Act
            val result = messageSubscriptionService.subscribeToMessage(
                processInstanceId,
                nodeId,
                messageName,
                correlationKey
            )

            // Assert
            result.timeoutAt shouldBe null
        }
    }

    context("receiveMessage") {
        test("should receive message and update subscription") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"
            val messagePayload = mapOf(
                "amount" to 100.0,
                "currency" to "USD"
            )

            val existingSubscription = MessageSubscription(
                id = 1,
                processInstanceId = 100,
                nodeId = "message-event-1",
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageSubscriptionStatus.AWAITING
            )

            val updatedSubscription = existingSubscription.copy(
                status = MessageSubscriptionStatus.RECEIVED,
                messagePayload = messagePayload,
                receivedAt = LocalDateTime.now()
            )

            every {
                mockMessageSubscriptionRepository.findByMessageNameAndCorrelationKeyAndStatus(
                    messageName,
                    correlationKey,
                    MessageSubscriptionStatus.AWAITING
                )
            } returns existingSubscription

            every { mockMessageSubscriptionRepository.save(any()) } returns updatedSubscription

            // Act
            val result = messageSubscriptionService.receiveMessage(messageName, correlationKey, messagePayload)

            // Assert
            result shouldNotBe null
            result?.status shouldBe MessageSubscriptionStatus.RECEIVED
            result?.messagePayload shouldBe messagePayload
            result?.receivedAt shouldNotBe null
        }

        test("should return null when subscription not found") {
            // Arrange
            val messageName = "NonExistentMessage"
            val correlationKey = "order-999"

            every {
                mockMessageSubscriptionRepository.findByMessageNameAndCorrelationKeyAndStatus(
                    messageName,
                    correlationKey,
                    MessageSubscriptionStatus.AWAITING
                )
            } returns null

            // Act
            val result = messageSubscriptionService.receiveMessage(messageName, correlationKey)

            // Assert
            result shouldBe null
        }

        test("should handle message without payload") {
            // Arrange
            val messageName = "PaymentReceived"
            val correlationKey = "order-123"

            val existingSubscription = MessageSubscription(
                id = 1,
                processInstanceId = 100,
                nodeId = "message-event-1",
                messageName = messageName,
                correlationKey = correlationKey,
                status = MessageSubscriptionStatus.AWAITING
            )

            val updatedSubscription = existingSubscription.copy(
                status = MessageSubscriptionStatus.RECEIVED,
                receivedAt = LocalDateTime.now()
            )

            every {
                mockMessageSubscriptionRepository.findByMessageNameAndCorrelationKeyAndStatus(
                    messageName,
                    correlationKey,
                    MessageSubscriptionStatus.AWAITING
                )
            } returns existingSubscription

            every { mockMessageSubscriptionRepository.save(any()) } returns updatedSubscription

            // Act
            val result = messageSubscriptionService.receiveMessage(messageName, correlationKey, null)

            // Assert
            result?.messagePayload shouldBe null
        }
    }

    context("timeoutSubscription") {
        test("should mark subscription as timed out") {
            // Arrange
            val subscriptionId = 1L
            val subscription = MessageSubscription(
                id = subscriptionId,
                processInstanceId = 100,
                nodeId = "message-event-1",
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.AWAITING
            )

            val timedOutSubscription = subscription.copy(status = MessageSubscriptionStatus.TIMEOUT)

            every { mockMessageSubscriptionRepository.findById(subscriptionId) } returns Optional.of(subscription)
            every { mockMessageSubscriptionRepository.save(any()) } returns timedOutSubscription

            // Act
            val result = messageSubscriptionService.timeoutSubscription(subscriptionId)

            // Assert
            result shouldNotBe null
            result?.status shouldBe MessageSubscriptionStatus.TIMEOUT
        }

        test("should return null when subscription not found") {
            // Arrange
            val subscriptionId = 999L
            every { mockMessageSubscriptionRepository.findById(subscriptionId) } returns Optional.empty()

            // Act
            val result = messageSubscriptionService.timeoutSubscription(subscriptionId)

            // Assert
            result shouldBe null
        }
    }

    context("getSubscriptionByInstanceAndNode") {
        test("should retrieve subscription by instance and node") {
            // Arrange
            val processInstanceId = 100L
            val nodeId = "message-event-1"
            val subscription = MessageSubscription(
                id = 1,
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.AWAITING
            )

            every { mockMessageSubscriptionRepository.findByProcessInstanceIdAndNodeId(processInstanceId, nodeId) } returns subscription

            // Act
            val result = messageSubscriptionService.getSubscriptionByInstanceAndNode(processInstanceId, nodeId)

            // Assert
            result shouldNotBe null
            result?.id shouldBe 1
            result?.nodeId shouldBe nodeId
        }

        test("should return null when subscription not found") {
            // Arrange
            val processInstanceId = 999L
            val nodeId = "nonexistent-node"

            every { mockMessageSubscriptionRepository.findByProcessInstanceIdAndNodeId(processInstanceId, nodeId) } returns null

            // Act
            val result = messageSubscriptionService.getSubscriptionByInstanceAndNode(processInstanceId, nodeId)

            // Assert
            result shouldBe null
        }
    }

    context("findExpiredSubscriptions") {
        test("should find subscriptions that have expired") {
            // Arrange
            val now = LocalDateTime.now()
            val expiredSubscription1 = MessageSubscription(
                id = 1,
                processInstanceId = 100,
                nodeId = "message-event-1",
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = now.minusMinutes(5)
            )
            val expiredSubscription2 = MessageSubscription(
                id = 2,
                processInstanceId = 101,
                nodeId = "message-event-2",
                messageName = "ApprovalRequested",
                correlationKey = "order-124",
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = now.minusMinutes(10)
            )

            every { mockMessageSubscriptionRepository.findExpiredSubscriptions(now) } returns listOf(
                expiredSubscription1,
                expiredSubscription2
            )

            // Act
            val result = messageSubscriptionService.findExpiredSubscriptions(now)

            // Assert
            result shouldHaveSize 2
            result[0].id shouldBe 1
            result[1].id shouldBe 2
        }

        test("should return empty list when no subscriptions expired") {
            // Arrange
            val now = LocalDateTime.now()
            every { mockMessageSubscriptionRepository.findExpiredSubscriptions(now) } returns emptyList()

            // Act
            val result = messageSubscriptionService.findExpiredSubscriptions(now)

            // Assert
            result.shouldBeEmpty()
        }
    }

    context("getPendingSubscriptionsForInstance") {
        test("should retrieve pending subscriptions for process instance") {
            // Arrange
            val processInstanceId = 100L
            val subscription1 = MessageSubscription(
                id = 1,
                processInstanceId = processInstanceId,
                nodeId = "message-event-1",
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.AWAITING
            )
            val subscription2 = MessageSubscription(
                id = 2,
                processInstanceId = processInstanceId,
                nodeId = "message-event-2",
                messageName = "ApprovalRequested",
                correlationKey = "order-124",
                status = MessageSubscriptionStatus.AWAITING
            )
            val subscription3 = MessageSubscription(
                id = 3,
                processInstanceId = processInstanceId,
                nodeId = "message-event-3",
                messageName = "Rejected",
                correlationKey = "order-125",
                status = MessageSubscriptionStatus.RECEIVED
            )

            every { mockMessageSubscriptionRepository.findByProcessInstanceId(processInstanceId) } returns listOf(
                subscription1,
                subscription2,
                subscription3
            )

            // Act
            val result = messageSubscriptionService.getPendingSubscriptionsForInstance(processInstanceId)

            // Assert
            result shouldHaveSize 2
            result.all { it.status == MessageSubscriptionStatus.AWAITING } shouldBe true
        }

        test("should return empty list when no pending subscriptions") {
            // Arrange
            val processInstanceId = 999L
            every { mockMessageSubscriptionRepository.findByProcessInstanceId(processInstanceId) } returns emptyList()

            // Act
            val result = messageSubscriptionService.getPendingSubscriptionsForInstance(processInstanceId)

            // Assert
            result.shouldBeEmpty()
        }
    }

    context("deleteSubscription") {
        test("should delete subscription") {
            // Arrange
            val subscriptionId = 1L
            every { mockMessageSubscriptionRepository.deleteById(subscriptionId) } just runs

            // Act
            messageSubscriptionService.deleteSubscription(subscriptionId)

            // Assert
            verify { mockMessageSubscriptionRepository.deleteById(subscriptionId) }
        }
    }
})
