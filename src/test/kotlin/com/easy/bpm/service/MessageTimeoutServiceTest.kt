package com.easy.bpm.service

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.message.MessageSubscription
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.Optional

class MessageTimeoutServiceTest : FunSpec({
    val mockMessageSubscriptionRepository = mockk<MessageSubscriptionRepository>()
    val mockProcessInstanceRepository = mockk<ProcessInstanceRepository>()
    val mockProcessService = mockk<ProcessService>()
    val messageTimeoutService = MessageTimeoutService(
        mockMessageSubscriptionRepository,
        mockProcessInstanceRepository
        , mockProcessService
    )

    beforeEach {
        clearAllMocks()
    }

    context("handleSubscriptionTimeout") {
        test("should timeout awaiting subscription and fail process instance") {
            val subscription = MessageSubscription(
                id = 1L,
                processInstanceId = 10L,
                nodeId = "message-event-1",
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = LocalDateTime.now().minusSeconds(5)
            )
            val definition = ProcessDefinition(
                id = 1L,
                key = "process-key",
                processName = "Process",
                version = 1,
                definitionJson = "{}"
            )
            val instance = ProcessInstance(
                id = 10L,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("message-event-1")
            )

            every { mockMessageSubscriptionRepository.findByIdForUpdate(1L) } returns subscription
            every { mockMessageSubscriptionRepository.save(any()) } answers { firstArg() }
            every { mockProcessInstanceRepository.findById(10L) } returns Optional.of(instance)
            every { mockProcessInstanceRepository.save(any()) } answers { firstArg() }
            every { mockProcessService.handleSubscriptionTimeout(instance.id, subscription.nodeId) } returns false

            val result = messageTimeoutService.handleSubscriptionTimeout(1L)

            result shouldBe true
            subscription.status shouldBe MessageSubscriptionStatus.TIMEOUT
            instance.status shouldBe ProcessStatus.FAILED
            verify { mockMessageSubscriptionRepository.save(subscription) }
            verify { mockProcessInstanceRepository.save(instance) }
        }

        test("should ignore subscription already processed by another instance") {
            val subscription = MessageSubscription(
                id = 1L,
                processInstanceId = 10L,
                nodeId = "message-event-1",
                messageName = "PaymentReceived",
                correlationKey = "order-123",
                status = MessageSubscriptionStatus.RECEIVED
            )

            every { mockMessageSubscriptionRepository.findByIdForUpdate(1L) } returns subscription

            val result = messageTimeoutService.handleSubscriptionTimeout(1L)

            result shouldBe false
            verify(exactly = 0) { mockMessageSubscriptionRepository.save(any()) }
            verify(exactly = 0) { mockProcessInstanceRepository.findById(any()) }
        }

        test("should route timeout to error boundary when boundary exists") {
            val subscription = MessageSubscription(
                id = 3L,
                processInstanceId = 20L,
                nodeId = "message-event-2",
                messageName = "TimeoutEvent",
                correlationKey = "key-200",
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = LocalDateTime.now().minusSeconds(5)
            )

            val definition = ProcessDefinition(
                id = 2L,
                key = "proc-2",
                processName = "Proc2",
                version = 1,
                definitionJson = "{}"
            )

            val instance = ProcessInstance(
                id = 20L,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("message-event-2")
            )

            every { mockMessageSubscriptionRepository.findByIdForUpdate(3L) } returns subscription
            every { mockMessageSubscriptionRepository.save(any()) } answers { firstArg() }
            every { mockProcessInstanceRepository.findById(20L) } returns Optional.of(instance)
            // Simulate ProcessService handling the timeout via error boundary
            every { mockProcessService.handleSubscriptionTimeout(instance.id, subscription.nodeId) } returns true

            val result = messageTimeoutService.handleSubscriptionTimeout(3L)

            result shouldBe true
            subscription.status shouldBe MessageSubscriptionStatus.TIMEOUT
            // Instance should remain ACTIVE since boundary handled it
            instance.status shouldBe ProcessStatus.ACTIVE
            verify { mockMessageSubscriptionRepository.save(subscription) }
            verify(exactly = 0) { mockProcessInstanceRepository.save(instance) }
        }

        test("should continue flow for internal timer timeout") {
            val subscription = MessageSubscription(
                id = 4L,
                processInstanceId = 30L,
                nodeId = "timer-1",
                messageName = ProcessService.INTERNAL_TIMER_MESSAGE_NAME,
                correlationKey = "timer-30-timer-1",
                status = MessageSubscriptionStatus.AWAITING,
                timeoutAt = LocalDateTime.now().minusSeconds(3)
            )

            val definition = ProcessDefinition(
                id = 3L,
                key = "proc-3",
                processName = "Proc3",
                version = 1,
                definitionJson = "{}"
            )

            val instance = ProcessInstance(
                id = 30L,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("timer-1")
            )

            every { mockMessageSubscriptionRepository.findByIdForUpdate(4L) } returns subscription
            every { mockMessageSubscriptionRepository.save(any()) } answers { firstArg() }
            every { mockProcessInstanceRepository.findById(30L) } returns Optional.of(instance)
            every { mockProcessService.handleTimerTimeout(instance.id, subscription.nodeId) } returns true

            val result = messageTimeoutService.handleSubscriptionTimeout(4L)

            result shouldBe true
            subscription.status shouldBe MessageSubscriptionStatus.TIMEOUT
            verify { mockMessageSubscriptionRepository.save(subscription) }
            verify { mockProcessService.handleTimerTimeout(instance.id, subscription.nodeId) }
            verify(exactly = 0) { mockProcessInstanceRepository.save(any()) }
        }
    }

    context("processExpiredSubscriptions") {
        test("should process claimed expired subscriptions in batches") {
            val firstBatch = listOf(
                MessageSubscription(
                    id = 1L,
                    processInstanceId = 10L,
                    nodeId = "message-event-1",
                    messageName = "PaymentReceived",
                    correlationKey = "order-123",
                    status = MessageSubscriptionStatus.AWAITING,
                    timeoutAt = LocalDateTime.now().minusSeconds(5)
                ),
                MessageSubscription(
                    id = 2L,
                    processInstanceId = 11L,
                    nodeId = "message-event-2",
                    messageName = "ApprovalRequested",
                    correlationKey = "order-124",
                    status = MessageSubscriptionStatus.AWAITING,
                    timeoutAt = LocalDateTime.now().minusSeconds(10)
                )
            )

            every { mockMessageSubscriptionRepository.claimExpiredSubscriptions(any(), any()) } returns firstBatch
            every { mockMessageSubscriptionRepository.findByIdForUpdate(1L) } returns firstBatch[0]
            every { mockMessageSubscriptionRepository.findByIdForUpdate(2L) } returns firstBatch[1]
            every { mockMessageSubscriptionRepository.save(any()) } answers { firstArg() }
            every { mockProcessInstanceRepository.findById(any()) } returns Optional.empty()

            messageTimeoutService.processExpiredSubscriptions()

            verify(exactly = 1) { mockMessageSubscriptionRepository.claimExpiredSubscriptions(any(), any()) }
            verify { mockMessageSubscriptionRepository.findByIdForUpdate(1L) }
            verify { mockMessageSubscriptionRepository.findByIdForUpdate(2L) }
        }

        test("should do nothing when no expired subscriptions are claimed") {
            every { mockMessageSubscriptionRepository.claimExpiredSubscriptions(any(), any()) } returns emptyList()

            messageTimeoutService.processExpiredSubscriptions()

            verify(exactly = 1) { mockMessageSubscriptionRepository.claimExpiredSubscriptions(any(), any()) }
            verify(exactly = 0) { mockMessageSubscriptionRepository.findByIdForUpdate(any()) }
        }
    }
})

