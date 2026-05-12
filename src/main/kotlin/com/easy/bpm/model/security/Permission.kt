package com.easy.bpm.model.security

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_permission")
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    val code: String,

    @Column(nullable = false, length = 200)
    val name: String,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

