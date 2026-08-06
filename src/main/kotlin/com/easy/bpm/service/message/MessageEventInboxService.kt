package com.easy.bpm.service

import com.easy.bpm.enum.MessageEventInboxStatus
import com.easy.bpm.model.message.MessageEventInbox
import com.easy.bpm.repository.message.MessageEventInboxRepository
import com.easy.bpm.service.process.ProcessService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class MessageEventInboxService(
    private val messageEventInboxRepository: MessageEventInboxRepository,
    private val processService: ProcessService
) {

    fun acceptExternalMessage(
        messageId: String?,
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>?
    ): ExternalMessageAcceptance {
        val effectiveMessageId = messageId?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val existing = messageEventInboxRepository.findByMessageId(effectiveMessageId)
        if (existing != null) {
            return ExternalMessageAcceptance(existing, duplicate = true)
        }

        val inboxMessage = try {
            messageEventInboxRepository.save(
                MessageEventInbox(
                    messageId = effectiveMessageId,
                    messageName = messageName,
                    correlationKey = correlationKey,
                    payload = variables
                )
            )
        } catch (_: DataIntegrityViolationException) {
            val duplicate = messageEventInboxRepository.findByMessageId(effectiveMessageId)
                ?: throw IllegalStateException("Message id '$effectiveMessageId' already exists but could not be loaded")
            return ExternalMessageAcceptance(duplicate, duplicate = true)
        }

        try {
            processService.handleMessageReceived(messageName, correlationKey, variables)
            inboxMessage.status = MessageEventInboxStatus.PROCESSED
            inboxMessage.processedAt = LocalDateTime.now()
        } catch (ex: IllegalArgumentException) {
            inboxMessage.status = MessageEventInboxStatus.UNMATCHED
            inboxMessage.errorMessage = ex.message?.take(1000)
            inboxMessage.processedAt = LocalDateTime.now()
        } catch (ex: Exception) {
            inboxMessage.status = MessageEventInboxStatus.FAILED
            inboxMessage.errorMessage = (ex.message ?: ex.javaClass.simpleName).take(1000)
            inboxMessage.processedAt = LocalDateTime.now()
        }

        return ExternalMessageAcceptance(messageEventInboxRepository.save(inboxMessage), duplicate = false)
    }
}

data class ExternalMessageAcceptance(
    val message: MessageEventInbox,
    val duplicate: Boolean
)
