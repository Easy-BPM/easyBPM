package com.easy.bpm.repository.message

import com.easy.bpm.model.message.MessageEventInbox
import org.springframework.data.jpa.repository.JpaRepository

interface MessageEventInboxRepository : JpaRepository<MessageEventInbox, Long> {
    fun findByMessageId(messageId: String): MessageEventInbox?
}
