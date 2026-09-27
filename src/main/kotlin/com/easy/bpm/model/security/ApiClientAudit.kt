package com.easy.bpm.model.security

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

enum class ApiClientAuditCategory { LIFECYCLE, USE }

@Entity
@Table(name = "api_client_audit")
class ApiClientAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_client_id", nullable = false)
    val apiClient: ApiClient,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val category: ApiClientAuditCategory,

    @Column(nullable = false, length = 40)
    val action: String,

    @Column(nullable = false, length = 20)
    var outcome: String,

    @Column(length = 255)
    val actor: String? = null,

    @Column(name = "credential_generation")
    val credentialGeneration: Int? = null,

    @Column(name = "remote_ip", length = 64)
    val remoteIp: String? = null,

    @Column(name = "http_method", length = 12)
    val httpMethod: String? = null,

    @Column(name = "request_path", length = 1024)
    val requestPath: String? = null,

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Column(name = "request_id", length = 128)
    val requestId: String? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", columnDefinition = "jsonb")
    val changedFields: JsonNode? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
