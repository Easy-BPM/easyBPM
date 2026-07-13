package com.easy.bpm.model.variable

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
data class ProcessVariable(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false, length = 100)
    val tenantId: String = "default",

    val processInstanceId: Long,

    val name: String,

    @Type(JsonBinaryType::class)
    @Column(columnDefinition = "jsonb")
    var value: JsonNode,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now()
)
