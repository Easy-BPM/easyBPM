package com.easy.bpm.model.worker

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "worker_request", uniqueConstraints = [UniqueConstraint(columnNames = ["idempotency_key"])])
data class WorkerRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val processInstanceId: Long,

    @Column(nullable = false)
    val nodeId: String,

    @Column(nullable = false, unique = true)
    val idempotencyKey: String,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkerRequestStatus = WorkerRequestStatus.PENDING,

    @Column(length = 1000)
    var lastError: String? = null,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var lastAttemptAt: LocalDateTime? = null,
    var completedAt: LocalDateTime? = null
)

enum class WorkerRequestStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    DLQ
}

