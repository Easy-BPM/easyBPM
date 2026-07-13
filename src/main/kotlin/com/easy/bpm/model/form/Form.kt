package com.easy.bpm.model.form

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type
import java.time.LocalDateTime

@Entity
data class Form(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false, length = 100)
    val tenantId: String = "default",

    @Column(name = "form_id", nullable = false)
    val formId: String,

    val name: String,

    @Type(JsonBinaryType::class)
    @Column(columnDefinition = "jsonb")
    val schema: JsonNode,

    val version: Int = 1,

    val createdAt: LocalDateTime = LocalDateTime.now()
)

