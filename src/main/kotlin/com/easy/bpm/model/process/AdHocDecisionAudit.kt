package com.easy.bpm.model.process

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
@Table(name = "ad_hoc_decision_audit")
data class AdHocDecisionAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "process_instance_id", nullable = false)
    val processInstanceId: Long,

    @Column(name = "ad_hoc_node_id", nullable = false, length = 255)
    val adHocNodeId: String,

    @Column(name = "activity_node_id", length = 255)
    val activityNodeId: String? = null,

    @Column(name = "decision_type", nullable = false, length = 100)
    val decisionType: String,

    @Column(name = "actor_type", nullable = false, length = 50)
    val actorType: String = "system",

    @Column(name = "actor_id", length = 255)
    val actorId: String? = null,

    @Column(name = "confidence")
    val confidence: Double? = null,

    @Column(name = "recommendation", columnDefinition = "TEXT")
    val recommendation: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "details", columnDefinition = "jsonb")
    val details: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

