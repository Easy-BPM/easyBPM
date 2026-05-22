package com.easy.bpm.ai.repository

import com.easy.bpm.ai.entity.AICredential
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for AI credential persistence and querying.
 */
@Repository
interface AICredentialRepository : JpaRepository<AICredential, String> {
    
    /**
     * Find credential by provider and owner (unique constraint).
     */
    fun findByProviderIdAndOwnerId(providerId: String, ownerId: String): Optional<AICredential>
    
    /**
     * Find all credentials for a user.
     */
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<AICredential>
    
    /**
     * Find credential by ID, with owner verification (security check).
     */
    fun findByIdAndOwnerId(id: String, ownerId: String): Optional<AICredential>
    
    /**
     * Find all active credentials by provider.
     */
    @Query("SELECT c FROM AICredential c WHERE c.providerId = :providerId AND c.isActive = true ORDER BY c.lastUsedAt DESC NULLS LAST")
    fun findActiveByProviderId(providerId: String): List<AICredential>
    
    /**
     * Find credentials not used since a date (for cleanup).
     */
    @Query("SELECT c FROM AICredential c WHERE c.lastUsedAt IS NULL OR c.lastUsedAt < :beforeDate")
    fun findStaleCredentials(beforeDate: LocalDateTime): List<AICredential>
    
    /**
     * Count active credentials by provider.
     */
    fun countByProviderIdAndIsActive(providerId: String, isActive: Boolean): Long
    
    /**
     * Find recently created credentials (for audit).
     */
    @Query("SELECT c FROM AICredential c WHERE c.createdAt > :since ORDER BY c.createdAt DESC")
    fun findRecentlyCreated(since: LocalDateTime): List<AICredential>
}
