package com.easy.bpm.repository.worker

import com.easy.bpm.model.worker.WorkerRequest
import com.easy.bpm.model.worker.WorkerRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WorkerRequestRepository : JpaRepository<WorkerRequest, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): WorkerRequest?
    
    fun findByProcessInstanceIdAndNodeId(processInstanceId: Long, nodeId: String): WorkerRequest?
    
    @Query("FROM WorkerRequest WHERE status = :status AND lastAttemptAt < :before")
    fun findRetryableFailed(
        @Param("status") status: WorkerRequestStatus,
        @Param("before") before: LocalDateTime
    ): List<WorkerRequest>
    
    @Query("FROM WorkerRequest WHERE status = 'DLQ'")
    fun findDlqMessages(): List<WorkerRequest>

    @Query("FROM WorkerRequest WHERE status IN :statuses AND lastAttemptAt < :before")
    fun findTimedOutRequests(
        @Param("statuses") statuses: Collection<WorkerRequestStatus>,
        @Param("before") before: LocalDateTime
    ): List<WorkerRequest>

    @Query(
        value = """
            SELECT *
            FROM worker_request
            WHERE status IN (:statuses)
              AND last_attempt_at < :before
            ORDER BY last_attempt_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimTimedOutRequests(
        @Param("statuses") statuses: Collection<String>,
        @Param("before") before: LocalDateTime,
        @Param("batchSize") batchSize: Int
    ): List<WorkerRequest>

    fun deleteByProcessInstanceId(processInstanceId: Long)
}

