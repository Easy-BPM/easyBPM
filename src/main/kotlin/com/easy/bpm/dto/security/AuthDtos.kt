package com.easy.bpm.dto.security

data class LoginRequest(
    val username: String,
    val password: String,
    val tenantId: String? = null
)

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val username: String,
    val tenantId: String,
    val groups: Set<String>,
    val permissions: Set<String>
)

data class CurrentUserResponse(
    val username: String,
    val tenantId: String,
    val groups: Set<String>,
    val permissions: Set<String>
)

