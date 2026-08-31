package com.easy.bpm.ai.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

/**
 * JPA entity for storing encrypted AI provider credentials.
 * Sensitive token data is encrypted at rest using Spring Security Crypto.
 */
@Entity
@Table(
    name = "ai_credentials",
    uniqueConstraints = [
        UniqueConstraint(columnNames = arrayOf("owner_id", "secret_name"), name = "uk_ai_creds_owner_secret_name")
    ],
    indexes = [
        Index(name = "idx_ai_creds_owner", columnList = "owner_id"),
        Index(name = "idx_ai_creds_provider", columnList = "provider_id"),
        Index(name = "idx_ai_creds_created", columnList = "created_at")
    ]
)
data class AICredential(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @Column(name = "provider_id", nullable = false, length = 50)
    val providerId: String,                                      // 'openai', 'anthropic', 'gemini', etc.

    @Column(name = "secret_name", nullable = false, length = 100)
    var secretName: String = providerId,                          // Stable reference shown in Admin/Modeler
    
    @Column(name = "credential_type", nullable = false, length = 20)
    var credentialType: String,                                  // API_KEY, BEARER, BASIC_AUTH
    
    @Column(name = "encrypted_token", nullable = false, length = 2048)
    var encryptedToken: String,                                  // Encrypted at rest
    
    @Column(name = "owner_id", nullable = false, length = 100)
    val ownerId: String,                                         // User ID (from security context)
    
    @ElementCollection
    @CollectionTable(name = "ai_credential_permissions", joinColumns = [JoinColumn(name = "credential_id")])
    @Column(name = "role")
    val permissions: MutableSet<String> = mutableSetOf(),        // RBAC: roles allowed to use this credential
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,
    
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    
    @Column(name = "description", length = 500)
    var description: String? = null
) {
    /**
     * Update last used timestamp (called after successful execution).
     */
    fun updateLastUsed() {
        lastUsedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * Check if credential is accessible to a given user with a role.
     */
    fun isAccessibleBy(userId: String, userRole: String): Boolean {
        val hasOwnerAccess = this.ownerId == userId || this.ownerId == "__workspace__"
        return hasOwnerAccess && this.isActive &&
               (this.permissions.isEmpty() || this.permissions.contains(userRole))
    }
    
    /**
     * Deactivate credential (soft delete for audit trail).
     */
    fun deactivate() {
        isActive = false
        updatedAt = LocalDateTime.now()
    }
}

/**
 * Lightweight credential summary for list/search responses.
 * No sensitive data, just metadata.
 */
data class AICredentialSummary(
    val id: String,
    val name: String,
    val providerId: String,
    val credentialType: String,
    val maskedToken: String,                         // Last 4 chars visible: "sk-***...hfaX"
    val reference: String,
    val createdAt: LocalDateTime,
    val lastUsedAt: LocalDateTime? = null,
    val description: String? = null
)
