package com.easy.bpm.repository.message

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.model.message.MessageSubscription
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface MessageSubscriptionRepository : JpaRepository<MessageSubscription, Long> {

    fun findByMessageNameAndCorrelationKeyAndStatus(
        messageName: String,
        correlationKey: String,
        status: MessageSubscriptionStatus
    ): MessageSubscription?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByMessageNameAndCorrelationKeyAndStatusOrderByIdAsc(
        messageName: String,
        correlationKey: String,
        status: MessageSubscriptionStatus
    ): MessageSubscription?

    fun findByProcessInstanceIdAndNodeId(
        processInstanceId: Long,
        nodeId: String
    ): MessageSubscription?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("FROM MessageSubscription WHERE id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): MessageSubscription?

    @Query("FROM MessageSubscription WHERE status = 'AWAITING' AND timeoutAt IS NOT NULL AND timeoutAt < :now")
    fun findExpiredSubscriptions(@Param("now") now: LocalDateTime): List<MessageSubscription>

    @Query(
        value = """
            SELECT *
            FROM message_subscription
            WHERE status = 'AWAITING'
              AND timeout_at IS NOT NULL
              AND timeout_at < :now
            ORDER BY timeout_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimExpiredSubscriptions(
        @Param("now") now: LocalDateTime,
        @Param("batchSize") batchSize: Int
    ): List<MessageSubscription>

    fun findByProcessInstanceId(processInstanceId: Long): List<MessageSubscription>
    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int

    @Query("FROM MessageSubscription WHERE status = 'AWAITING'")
    fun findAllPendingSubscriptions(): List<MessageSubscription>
}

