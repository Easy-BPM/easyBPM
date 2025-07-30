package com.easy.bpm.model.variable

import jakarta.persistence.*

@Entity
data class ProcessVariable(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val processInstanceId: Long,

    val name: String,

    @Lob
    val value: String
)