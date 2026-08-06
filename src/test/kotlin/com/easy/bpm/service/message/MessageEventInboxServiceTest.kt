package com.easy.bpm.service.message

import com.easy.bpm.enum.MessageEventInboxStatus
import com.easy.bpm.model.message.MessageEventInbox
import com.easy.bpm.repository.message.MessageEventInboxRepository
import com.easy.bpm.service.process.ProcessService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class MessageEventInboxServiceTest : FunSpec({
    val inboxRepository = mockk<MessageEventInboxRepository>()
    val processService = mockk<ProcessService>()
    val service = MessageEventInboxService(inboxRepository, processService)

    beforeEach {
        io.mockk.clearAllMocks()
    }

    test("should process a new external message once") {
        every { inboxRepository.findByMessageId("msg-1") } returns null
        every { inboxRepository.save(any()) } answers { firstArg() }
        every {
            processService.handleMessageReceived(
                "invoice-received",
                "ORDER-1",
                mapOf("invoiceId" to "INV-1")
            )
        } just runs

        val result = service.acceptExternalMessage(
            "msg-1",
            "invoice-received",
            "ORDER-1",
            mapOf("invoiceId" to "INV-1")
        )

        result.duplicate shouldBe false
        result.message.status shouldBe MessageEventInboxStatus.PROCESSED
        verify(exactly = 1) {
            processService.handleMessageReceived(
                "invoice-received",
                "ORDER-1",
                mapOf("invoiceId" to "INV-1")
            )
        }
    }

    test("should return existing result for duplicate message id") {
        val existing = MessageEventInbox(
            id = 1L,
            messageId = "msg-1",
            messageName = "invoice-received",
            correlationKey = "ORDER-1",
            status = MessageEventInboxStatus.PROCESSED
        )

        every { inboxRepository.findByMessageId("msg-1") } returns existing

        val result = service.acceptExternalMessage("msg-1", "invoice-received", "ORDER-1", null)

        result.duplicate shouldBe true
        result.message.status shouldBe MessageEventInboxStatus.PROCESSED
        verify(exactly = 0) { processService.handleMessageReceived(any(), any(), any()) }
    }

    test("should mark message unmatched when no process is waiting") {
        every { inboxRepository.findByMessageId("msg-2") } returns null
        every { inboxRepository.save(any()) } answers { firstArg() }
        every {
            processService.handleMessageReceived("invoice-received", "ORDER-2", null)
        } throws IllegalArgumentException("No waiting subscription")

        val result = service.acceptExternalMessage("msg-2", "invoice-received", "ORDER-2", null)

        result.duplicate shouldBe false
        result.message.status shouldBe MessageEventInboxStatus.UNMATCHED
        result.message.errorMessage shouldBe "No waiting subscription"
    }
})
