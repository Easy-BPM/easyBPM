package com.easy.bpm.repository.message

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.model.message.MessageSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface MessageSubscriptionRepository : JpaRepository<MessageSubscription, Long> {
    
    fun findByMessageNameAndCorrelationKeyAndStatus(
        messageName: String,
        correlationKey: String,
        status: MessageSubscriptionStatus
    ): MessageSubscription?

    fun findByProcessInstanceIdAndNodeId(
        processInstanceId: Long,
        nodeId: String
    ): MessageSubscription?

    @Query("FROM MessageSubscription WHERE status = 'AWAITING' AND timeoutAt IS NOT NULL AND timeoutAt < :now")
    fun findExpiredSubscriptions(@Param("now") now: LocalDateTime): List<MessageSubscription>

    fun findByProcessInstanceId(processInstanceId: Long): List<MessageSubscription>

    @Query("FROM MessageSubscription WHERE status = 'AWAITING'")
    fun findAllPendingSubscriptions(): List<MessageSubscription>
}
