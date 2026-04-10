package com.easy.bpm.service

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MessageTimeoutService(
    private val messageSubscriptionRepository: MessageSubscriptionRepository,
    private val processInstanceRepository: ProcessInstanceRepository
) {
    private val logger = LoggerFactory.getLogger(MessageTimeoutService::class.java)

    /**
     * Check for expired message subscriptions and handle timeouts.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    @Transactional
    fun processExpiredSubscriptions() {
        try {
            val now = LocalDateTime.now()
            val expiredSubscriptions = messageSubscriptionRepository.findExpiredSubscriptions(now)

            if (expiredSubscriptions.isEmpty()) {
                return
            }

            logger.info("Processing ${expiredSubscriptions.size} expired message subscriptions")

            expiredSubscriptions.forEach { subscription ->
                try {
                    handleSubscriptionTimeout(subscription.id)
                    logger.info("Timeout handled for subscription ${subscription.id}: message=${subscription.messageName}, correlationKey=${subscription.correlationKey}")
                } catch (ex: Exception) {
                    logger.error("Error handling timeout for subscription ${subscription.id}", ex)
                }
            }
        } catch (ex: Exception) {
            logger.error("Error in processExpiredSubscriptions", ex)
        }
    }

    /**
     * Handle timeout for a specific message subscription
     */
    @Transactional
    fun handleSubscriptionTimeout(subscriptionId: Long): Boolean {
        val subscription = messageSubscriptionRepository.findById(subscriptionId).orElse(null) ?: return false

        // Mark subscription as timed out
        subscription.status = MessageSubscriptionStatus.TIMEOUT
        subscription.receivedAt = LocalDateTime.now()
        messageSubscriptionRepository.save(subscription)

        // Mark process instance as failed
        val instance = processInstanceRepository.findById(subscription.processInstanceId).orElse(null) ?: return true

        instance.status = ProcessStatus.FAILED
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        logger.warn(
            "Message subscription ${subscription.id} timed out. " +
                "Message: ${subscription.messageName}, CorrelationKey: ${subscription.correlationKey}, " +
                "ProcessInstanceId: ${subscription.processInstanceId}"
        )

        return true
    }
}
