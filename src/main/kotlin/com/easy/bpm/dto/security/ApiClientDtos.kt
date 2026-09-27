package com.easy.bpm.dto.security

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

data class CreateApiClientRequest(
    val name: String = "",
    val description: String? = null,
    val permissionCodes: Set<String> = emptySet(),
    val expiresAt: LocalDateTime? = null
)

data class UpdateApiClientRequest(
    val name: String? = null,
    val description: String? = null,
    val permissionCodes: Set<String>? = null,
    val expiresAt: LocalDateTime? = null
)

data class RotateApiClientRequest(val expiresAt: LocalDateTime? = null)

data class ApiClientResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val status: String,
    val expiresAt: LocalDateTime,
    val permissionCodes: Set<String>,
    val credentialGeneration: Int,
    val lastUsedAt: LocalDateTime?,
    val lastUsedIp: String?,
    val revokedAt: LocalDateTime?,
    val revokedBy: String?,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val updatedAt: LocalDateTime,
    val updatedBy: String,
    val version: Long
)

data class ApiClientCredentialResponse(
    val client: ApiClientResponse,
    val credential: String
)

data class ApiClientAuditResponse(
    val id: Long,
    val category: String,
    val action: String,
    val outcome: String,
    val actor: String?,
    val credentialGeneration: Int?,
    val remoteIp: String?,
    val httpMethod: String?,
    val requestPath: String?,
    val httpStatus: Int?,
    val requestId: String?,
    val durationMs: Long?,
    val changedFields: JsonNode?,
    val createdAt: LocalDateTime
)

data class AssignablePermissionResponse(val code: String, val name: String)

data class ApiClientErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val correlationId: String,
    val fieldErrors: Map<String, String> = emptyMap()
)
