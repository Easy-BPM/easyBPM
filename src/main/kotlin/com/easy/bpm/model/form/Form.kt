package com.easy.bpm.model.form

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
data class Form(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "form_id", nullable = false)
    val formId: String,

    val name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val schema: JsonNode,

    val version: Int = 1,

    val createdAt: LocalDateTime = LocalDateTime.now()
)
