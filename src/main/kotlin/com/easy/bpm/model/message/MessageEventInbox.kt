package com.easy.bpm.model.message

import com.easy.bpm.enum.MessageEventInboxStatus
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
@Table(
    name = "message_event_inbox",
    uniqueConstraints = [UniqueConstraint(name = "uk_message_event_inbox_message_id", columnNames = ["message_id"])]
)
data class MessageEventInbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false, length = 100)
    val tenantId: String = "default",

    @Column(name = "message_id", nullable = false, length = 255)
    val messageId: String,

    @Column(name = "message_name", nullable = false, length = 255)
    val messageName: String,

    @Column(name = "correlation_key", nullable = false, length = 255)
    val correlationKey: String,

    @Type(JsonBinaryType::class)
    @Column(name = "payload", columnDefinition = "jsonb")
    var payload: Map<String, Any>? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: MessageEventInboxStatus = MessageEventInboxStatus.RECEIVED,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
)
