package com.easy.bpm.model.message

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDateTime


@Entity
data class MessageSubscription(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(name = "process_instance_id", nullable = false)
        val processInstanceId: Long,

        @Column(nullable = false)
        val nodeId: String,

        @Column(nullable = false)
        val messageName: String,

        @Column(nullable = false)
        val correlationKey: String,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var status: MessageSubscriptionStatus = MessageSubscriptionStatus.AWAITING,

        @Type(JsonBinaryType::class)
        @Column(columnDefinition = "jsonb")
        var messagePayload: Map<String, Any>? = null,

        var timeoutAt: LocalDateTime? = null,

        val createdAt: LocalDateTime = LocalDateTime.now(),

        var receivedAt: LocalDateTime? = null
)
