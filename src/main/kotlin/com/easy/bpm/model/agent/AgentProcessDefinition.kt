package com.easy.bpm.model.agent

import com.fasterxml.jackson.annotation.JsonProperty
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
@Table(
    name = "agent_process_definition",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_agent_process_definition_key_version",
            columnNames = ["process_key", "version"]
        )
    ]
)
data class AgentProcessDefinition(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "process_key", nullable = false)
    @get:JsonProperty("key")
    val key: String = "",

    @Column(name = "process_name")
    @get:JsonProperty("processName")
    val processName: String? = null,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    val definitionJson: String,

    @Column(nullable = false)
    val version: Int = 1,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
