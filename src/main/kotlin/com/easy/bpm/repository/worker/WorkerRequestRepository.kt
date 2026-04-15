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

    fun deleteByProcessInstanceId(processInstanceId: Long)
}
