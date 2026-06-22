package com.easy.bpm.service

import com.easy.bpm.model.worker.WorkerRequestStatus
import com.easy.bpm.repository.worker.WorkerRequestRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WorkerRequestTimeoutService(
    private val workerRequestRepository: WorkerRequestRepository,
    private val processService: ProcessService
) {
    private val logger = LoggerFactory.getLogger(WorkerRequestTimeoutService::class.java)

    companion object {
        private const val TIMEOUT_MINUTES = 2L
        private const val TIMEOUT_BATCH_SIZE = 100
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    @Transactional
    fun failTimedOutApiTasks() {
        val cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES)
        val timedOut = workerRequestRepository.claimTimedOutRequests(
            listOf(WorkerRequestStatus.IN_PROGRESS.name, WorkerRequestStatus.PENDING.name),
            cutoff,
            TIMEOUT_BATCH_SIZE
        )

        timedOut.forEach { request ->
            val message = "API task '${request.nodeId}' timed out after $TIMEOUT_MINUTES minutes without completion"
            logger.warn("Failing timed out API task: instance={}, node={}, request={}", request.processInstanceId, request.nodeId, request.id)

            request.status = WorkerRequestStatus.DLQ
            request.lastError = message
            request.completedAt = LocalDateTime.now()
            workerRequestRepository.save(request)

            processService.markServiceTaskTimedOut(request.processInstanceId, request.nodeId, message)
        }
    }
}
