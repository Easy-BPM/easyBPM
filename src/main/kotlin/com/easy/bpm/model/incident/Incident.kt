package com.easy.bpm.model.incident

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
    name = "incident",
    indexes = [
        Index(name = "idx_incident_status", columnList = "status"),
        Index(name = "idx_incident_source", columnList = "source"),
        Index(name = "idx_incident_process_instance", columnList = "process_instance_id"),
        Index(name = "idx_incident_created_at", columnList = "created_at DESC")
    ]
)
data class Incident(
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
    @Column(nullable = false, length = 50)
    var status: IncidentStatus = IncidentStatus.OPEN,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var severity: IncidentSeverity = IncidentSeverity.HIGH,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val source: IncidentSource = IncidentSource.PROCESS_ENGINE,

    @Column(nullable = false, columnDefinition = "TEXT")
    var message: String,

    @Column(name = "technical_details", columnDefinition = "TEXT")
    var technicalDetails: String? = null,

    @Column(name = "external_reference_id", length = 255)
    var externalReferenceId: String? = null,

    @Column(name = "occurrence_count", nullable = false)
    var occurrenceCount: Int = 1,

    @Column(name = "last_occurred_at", nullable = false)
    var lastOccurredAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "acknowledged_at")
    var acknowledgedAt: LocalDateTime? = null,

    @Column(name = "acknowledged_by", length = 255)
    var acknowledgedBy: String? = null,

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = null,

    @Column(name = "resolved_by", length = 255)
    var resolvedBy: String? = null,

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    var resolutionNote: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", length = 50)
    var resolutionAction: IncidentResolutionAction? = null
)

enum class IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}

enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class IncidentSource {
    PROCESS_ENGINE,
    WORKER,
    CODE_TASK,
    AI_TASK,
    MESSAGE
}

enum class IncidentResolutionAction {
    RESOLVED_MANUALLY,
    VARIABLE_FIXED,
    RETRIED_SUCCESSFULLY,
    IGNORED_KNOWN_ISSUE,
    INSTANCE_CANCELLED
}
