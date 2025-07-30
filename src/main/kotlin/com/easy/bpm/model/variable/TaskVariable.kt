package com.easy.bpm.model.variable

import jakarta.persistence.*

@Entity
data class TaskVariable(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val taskId: Long,

    val name: String,

    @Lob
    val value: String
)
