package com.easy.bpm.model.process

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "process_instance_event",
    indexes = [
        Index(name = "idx_process_instance_event_instance", columnList = "process_instance_id"),
        Index(name = "idx_process_instance_event_node", columnList = "node_id"),
        Index(name = "idx_process_instance_event_type", columnList = "event_type"),
        Index(name = "idx_process_instance_event_created_at", columnList = "created_at DESC")
    ]
)
data class ProcessInstanceEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false, length = 100)
    val tenantId: String = "default",

    @Column(name = "process_instance_id", nullable = false)
    val processInstanceId: Long,

    @Column(name = "node_id", length = 255)
    val nodeId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    val eventType: ProcessInstanceEventType,

    @Column(nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(name = "actor", length = 255)
    val actor: String? = null,

    @Column(name = "details", columnDefinition = "TEXT")
    val details: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ProcessInstanceEventType {
    PROCESS_STARTED,
    NODE_ENTERED,
    TASK_CREATED,
    TASK_CLAIMED,
    TASK_COMPLETED,
    WORKER_REQUESTED,
    WORKER_COMPLETED,
    WORKER_FAILED,
    MESSAGE_WAITING,
    MESSAGE_RECEIVED,
    MESSAGE_THROWN,
    TIMER_WAITING,
    TIMER_TRIGGERED,
    GATEWAY_EVALUATED,
    MANUAL_MOVE,
    INCIDENT_CREATED,
    INCIDENT_RETRY_REQUESTED,
    INCIDENT_RESOLVED,
    PROCESS_COMPLETED,
    PROCESS_FAILED,
    PROCESS_CANCELLED
}
