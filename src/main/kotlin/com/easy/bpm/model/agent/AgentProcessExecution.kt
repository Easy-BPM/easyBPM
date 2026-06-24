package com.easy.bpm.model.agent

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
@Table(name = "agent_process_execution")
data class AgentProcessExecution(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "agent_process_definition_id", nullable = false)
    val agentProcessDefinitionId: Long,

    @Column(name = "process_instance_id", nullable = false)
    val processInstanceId: Long,

    @Column(name = "node_id", nullable = false)
    val nodeId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: AgentProcessExecutionStatus = AgentProcessExecutionStatus.PLANNED,

    @Type(JsonBinaryType::class)
    @Column(name = "input_payload", columnDefinition = "jsonb")
    val inputPayload: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "decision_trace", columnDefinition = "jsonb")
    var decisionTrace: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    var outputPayload: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
)

enum class AgentProcessExecutionStatus {
    PLANNED,
    COMPLETED,
    FAILED
}
