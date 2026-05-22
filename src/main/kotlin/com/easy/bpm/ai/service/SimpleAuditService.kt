package com.easy.bpm.ai.service

import com.easy.bpm.ai.entity.AICredential
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * Simple in-memory audit logging implementation for credential access.
 * In production, replace with actual audit service that logs to database/external system.
 */
@Service
class SimpleAuditService : AuditService {
    
    private val auditLog = mutableListOf<AuditLogEntry>()
    
    override fun logCredentialAction(
        action: String,
        userId: String,
        providerId: String,
        credentialId: String?,
        success: Boolean
    ) {
        auditLog.add(
            AuditLogEntry(
                id = UUID.randomUUID().toString(),
                action = action,
                userId = userId,
                providerId = providerId,
                credentialId = credentialId,
                success = success,
                timestamp = LocalDateTime.now()
            )
        )
        
        // Log to SLF4J for monitoring
        val level = if (success) "INFO" else "WARN"
        val msg = "$action by $userId for provider $providerId - ${if (success) "SUCCESS" else "FAILED"}"
        println("[$level] $msg")
    }
    
    /**
     * Get audit log (for testing and debugging).
     */
    fun getAuditLog(userId: String? = null): List<AuditLogEntry> {
        return if (userId != null) {
            auditLog.filter { it.userId == userId }
        } else {
            auditLog.toList()
        }
    }
    
    /**
     * Clear audit log (for testing).
     */
    fun clearAuditLog() {
        auditLog.clear()
    }
}

/**
 * Simple audit log entry.
 */
data class AuditLogEntry(
    val id: String,
    val action: String,
    val userId: String,
    val providerId: String,
    val credentialId: String?,
    val success: Boolean,
    val timestamp: LocalDateTime
)
