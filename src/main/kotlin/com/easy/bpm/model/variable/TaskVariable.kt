package com.easy.bpm.model.variable

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
data class TaskVariable(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val taskId: Long,

    val name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    val value: JsonNode
)

