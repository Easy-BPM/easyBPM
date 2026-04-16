package com.easy.bpm.service

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.model.message.MessageSubscription
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MessageSubscriptionService(
    private val messageSubscriptionRepository: MessageSubscriptionRepository
) {

    /**
     * Create a new message subscription when a process reaches a MessageEvent node
     */
    @Transactional
    fun subscribeToMessage(
        processInstanceId: Long,
        nodeId: String,
        messageName: String,
        correlationKey: String,
        timeoutAt: LocalDateTime? = null
    ): MessageSubscription {
        val subscription = MessageSubscription(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            status = MessageSubscriptionStatus.AWAITING,
            timeoutAt = timeoutAt
        )
        return messageSubscriptionRepository.save(subscription)
    }

    /**
     * Receive a message and resume the waiting process
     */
    @Transactional
    fun receiveMessage(
        messageName: String,
        correlationKey: String,
        messagePayload: Map<String, Any>? = null
    ): MessageSubscription? {
        val subscription = messageSubscriptionRepository.findFirstByMessageNameAndCorrelationKeyAndStatusOrderByIdAsc(
            messageName,
            correlationKey,
            MessageSubscriptionStatus.AWAITING
        ) ?: return null

        subscription.status = MessageSubscriptionStatus.RECEIVED
        subscription.messagePayload = messagePayload
        subscription.receivedAt = LocalDateTime.now()

        return messageSubscriptionRepository.save(subscription)
    }

    /**
     * Handle timeout for a message subscription
     */
    @Transactional
    fun timeoutSubscription(subscriptionId: Long): MessageSubscription? {
        val subscription = messageSubscriptionRepository.findByIdForUpdate(subscriptionId) ?: return null
        if (subscription.status != MessageSubscriptionStatus.AWAITING) {
            return null
        }
        subscription.status = MessageSubscriptionStatus.TIMEOUT
        subscription.receivedAt = LocalDateTime.now()
        return messageSubscriptionRepository.save(subscription)
    }

    /**
     * Get subscription by process instance and node
     */
    fun getSubscriptionByInstanceAndNode(
        processInstanceId: Long,
        nodeId: String
    ): MessageSubscription? =
        messageSubscriptionRepository.findByProcessInstanceIdAndNodeId(processInstanceId, nodeId)

    /**
     * Find subscriptions that have timed out
     */
    fun findExpiredSubscriptions(now: LocalDateTime = LocalDateTime.now()): List<MessageSubscription> =
        messageSubscriptionRepository.findExpiredSubscriptions(now)

    /**
     * Find all pending subscriptions for a process instance
     */
    fun getPendingSubscriptionsForInstance(processInstanceId: Long): List<MessageSubscription> =
        messageSubscriptionRepository.findByProcessInstanceId(processInstanceId)
            .filter { it.status == MessageSubscriptionStatus.AWAITING }

    /**
     * Delete subscription after process advances past the message event
     */
    @Transactional
    fun deleteSubscription(subscriptionId: Long) {
        messageSubscriptionRepository.deleteById(subscriptionId)
    }

    /**
     * Mark subscription as failed
     */
    @Transactional
    @Suppress("UNUSED_PARAMETER")
    fun failSubscription(subscriptionId: Long, reason: String = ""): MessageSubscription? {
        val subscription = messageSubscriptionRepository.findById(subscriptionId).orElse(null) ?: return null
        subscription.status = MessageSubscriptionStatus.FAILED
        return messageSubscriptionRepository.save(subscription)
    }

    @Transactional
    fun deleteSubscriptionsForInstance(processInstanceId: Long) {
        messageSubscriptionRepository.deleteByProcessInstanceId(processInstanceId)
    }
}
