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
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processService: ProcessService
) {
    private val logger = LoggerFactory.getLogger(MessageTimeoutService::class.java)

    companion object {
        private const val TIMEOUT_BATCH_SIZE = 100
    }

    /**
     * Check for expired message subscriptions and handle timeouts.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    @Transactional
    fun processExpiredSubscriptions() {
        try {
            val now = LocalDateTime.now()
            var totalClaimed = 0

            while (true) {
                val claimedSubscriptions = messageSubscriptionRepository.claimExpiredSubscriptions(now, TIMEOUT_BATCH_SIZE)

                if (claimedSubscriptions.isEmpty()) {
                    return
                }

                totalClaimed += claimedSubscriptions.size
                logger.info("Processing ${claimedSubscriptions.size} claimed expired message subscriptions")

                claimedSubscriptions.forEach { subscription ->
                    try {
                        handleSubscriptionTimeout(subscription.id)
                        logger.info("Timeout handled for subscription ${subscription.id}: message=${subscription.messageName}, correlationKey=${subscription.correlationKey}")
                    } catch (ex: Exception) {
                        logger.error("Error handling timeout for subscription ${subscription.id}", ex)
                    }
                }

                if (claimedSubscriptions.size < TIMEOUT_BATCH_SIZE) {
                    logger.info("Finished processing $totalClaimed expired message subscriptions")
                    return
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
        val subscription = messageSubscriptionRepository.findByIdForUpdate(subscriptionId) ?: return false
        if (subscription.status != MessageSubscriptionStatus.AWAITING) {
            return false
        }

        // Mark subscription as timed out
        subscription.status = MessageSubscriptionStatus.TIMEOUT
        subscription.receivedAt = LocalDateTime.now()
        messageSubscriptionRepository.save(subscription)

        // Mark process instance as failed
        val instance = processInstanceRepository.findById(subscription.processInstanceId).orElse(null) ?: return true

        // Internal timer subscriptions should advance process flow when timeout is reached
        if (subscription.messageName == ProcessService.INTERNAL_TIMER_MESSAGE_NAME) {
            val timerHandled = try {
                processService.handleTimerTimeout(instance.id, subscription.nodeId)
            } catch (ex: Exception) {
                logger.error("Error while handling TimerEvent timeout for subscription ${subscription.id}", ex)
                false
            }

            if (timerHandled) {
                logger.info("Timer timeout handled for subscription ${subscription.id}: node=${subscription.nodeId}")
                return true
            }
        }

        // Try to route timeout to an attached ErrorBoundaryEvent; if handled, do not fail the instance
        val handledByBoundary = try {
            processService.handleSubscriptionTimeout(instance.id, subscription.nodeId)
        } catch (ex: Exception) {
            logger.error("Error while routing timeout to boundary for subscription ${subscription.id}", ex)
            false
        }

        if (!handledByBoundary) {
            instance.status = ProcessStatus.FAILED
            instance.updatedAt = LocalDateTime.now()
            processInstanceRepository.save(instance)

            logger.warn(
                "Message subscription ${subscription.id} timed out. " +
                    "Message: ${subscription.messageName}, CorrelationKey: ${subscription.correlationKey}, " +
                    "ProcessInstanceId: ${subscription.processInstanceId} - instance marked FAILED"
            )
        } else {
            logger.info("Message subscription ${subscription.id} timed out and was handled by an attached ErrorBoundaryEvent")
        }

        return true
    }
}

