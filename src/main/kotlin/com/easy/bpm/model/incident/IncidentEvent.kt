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
    name = "incident_event",
    indexes = [
        Index(name = "idx_incident_event_incident", columnList = "incident_id"),
        Index(name = "idx_incident_event_created_at", columnList = "created_at DESC")
    ]
)
data class IncidentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "incident_id", nullable = false)
    val incidentId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: IncidentEventType,

    @Column(nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(name = "actor", length = 255)
    val actor: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class IncidentEventType {
    CREATED,
    OCCURRED_AGAIN,
    ACKNOWLEDGED,
    RESOLVED,
    REOPENED,
    RETRY_REQUESTED
}
