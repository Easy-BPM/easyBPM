package com.easy.bpm.model.security

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class ApiClientLifecycleStatus { ACTIVE, REVOKED }

@Entity
@Table(name = "api_client")
class ApiClient(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 64)
    var selector: String,

    @Column(name = "normalized_name", nullable = false, unique = true, length = 100)
    val normalizedName: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @Column(name = "secret_hash", nullable = false, length = 100)
    var secretHash: String,

    @Column(name = "credential_generation", nullable = false)
    var credentialGeneration: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    var lifecycleStatus: ApiClientLifecycleStatus = ApiClientLifecycleStatus.ACTIVE,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,

    @Column(name = "last_used_ip", length = 64)
    var lastUsedIp: String? = null,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_by", length = 255)
    var revokedBy: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", nullable = false, length = 255)
    val createdBy: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_by", nullable = false, length = 255)
    var updatedBy: String,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "api_client_permission",
        joinColumns = [JoinColumn(name = "api_client_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    var permissions: MutableSet<Permission> = mutableSetOf(),

    @Version
    var version: Long = 0
)

