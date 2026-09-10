package com.easy.bpm.ai.service

import com.easy.bpm.ai.dto.AICredentialCreateRequestDto
import com.easy.bpm.ai.dto.AICredentialResponseDto
import com.easy.bpm.ai.dto.AICredentialUpdateRequestDto
import com.easy.bpm.ai.entity.AICredential
import com.easy.bpm.ai.repository.AICredentialRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for managing encrypted AI provider credentials.
 * Supports encryption at rest, environment variable resolution, and audit logging.
 */
@Service
@Transactional
class CredentialVault(
    private val credentialRepository: AICredentialRepository,
    @Autowired(required = false)
    private val auditService: AuditService? = null
) {
    companion object {
        const val WORKSPACE_OWNER_ID = "__workspace__"
        private const val AES_GCM_PREFIX = "gcm:"
        private const val AES_GCM_IV_BYTES = 12
        private const val AES_GCM_TAG_BITS = 128
        private const val KEY_ENV_VAR = "EASY_BPM_SERVER_AI_ENCRYPTION_KEY"
        private const val DEV_KEY = "default-dev-key-change-in-prod-1234"
        private val secureRandom = SecureRandom()
    }
    
    // In production, encryption key must come from EASY_BPM_SERVER_AI_ENCRYPTION_KEY.
    private val encryptionKey: SecretKey by lazy {
        val keyStr = System.getenv(KEY_ENV_VAR)
            ?: if (isProductionRuntime()) {
                throw IllegalStateException("$KEY_ENV_VAR must be configured before storing or reading AI credentials in production")
            } else {
                DEV_KEY
            }
        val keyBytes = keyStr
            .take(32)
            .padEnd(32, 'x')
            .toByteArray(Charsets.UTF_8)
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypt a plaintext token using AES-GCM with a random IV.
     * 
     * @param plaintext Raw credential token
     * @return Encrypted ciphertext (base64 encoded)
     */
    fun encrypt(plaintext: String): String {
        return try {
            val iv = ByteArray(AES_GCM_IV_BYTES)
            secureRandom.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(AES_GCM_TAG_BITS, iv))
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            AES_GCM_PREFIX + Base64.getEncoder().encodeToString(iv + encryptedBytes)
        } catch (e: Exception) {
            throw RuntimeException("Failed to encrypt credential: ${e.message}", e)
        }
    }
    
    /**
     * Decrypt an encrypted token.
     * 
     * @param ciphertext Encrypted credential
     * @return Decrypted plaintext
     */
    fun decrypt(ciphertext: String): String {
        return try {
            if (ciphertext.startsWith(AES_GCM_PREFIX)) {
                decryptGcm(ciphertext.removePrefix(AES_GCM_PREFIX))
            } else {
                decryptLegacyAes(ciphertext)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to decrypt credential: ${e.message}", e)
        }
    }
    
    /**
     * Store a new credential securely.
     * Encrypts token before persistence, enforces one credential per provider per user.
     * 
     * @param userId User ID from security context
     * @param request Credential create request (token will be encrypted)
     * @return Stored credential entity (encrypted)
     * @throws IllegalArgumentException if user already has credential for this provider
     */
    fun storeCredential(userId: String, request: AICredentialCreateRequestDto): AICredential {
        val secretName = normalizeSecretName(request.name ?: request.providerId)
        val existing = credentialRepository.findByOwnerIdAndSecretName(userId, secretName)
        if (existing.isPresent) {
            throw IllegalArgumentException("Credential already exists with name '$secretName' for user '$userId'")
        }
        
        // Encrypt token before storage
        val encryptedToken = encrypt(request.token)
        
        val credential = AICredential(
            id = UUID.randomUUID().toString(),
            providerId = request.providerId,
            secretName = secretName,
            credentialType = request.credentialType,
            encryptedToken = encryptedToken,
            maskedToken = maskToken(request.token),
            tokenFingerprint = fingerprint(request.token),
            ownerId = userId,
            permissions = request.permissions.toMutableSet(),
            description = request.description
        )
        
        val saved = credentialRepository.save(credential)
        
        // Audit log (optional)
        auditService?.logCredentialAction("CREATE", userId, request.providerId, success = true)
        
        return saved
    }

    fun storeWorkspaceSecret(request: AICredentialCreateRequestDto): AICredential =
        storeCredential(WORKSPACE_OWNER_ID, request)

    fun updateWorkspaceSecret(credentialId: String, request: AICredentialUpdateRequestDto): AICredential {
        val cred = credentialRepository.findByIdAndOwnerId(credentialId, WORKSPACE_OWNER_ID)
            .orElseThrow { IllegalArgumentException("Secret not found: $credentialId") }

        request.name?.let {
            val nextName = normalizeSecretName(it)
            val duplicate = credentialRepository.findByOwnerIdAndSecretName(WORKSPACE_OWNER_ID, nextName)
            if (duplicate.isPresent && duplicate.get().id != cred.id) {
                throw IllegalArgumentException("Secret already exists with name '$nextName'")
            }
            cred.secretName = nextName
        }
        request.credentialType?.takeIf { it.isNotBlank() }?.let { cred.credentialType = it.trim() }
        request.token?.takeIf { it.isNotBlank() }?.let {
            cred.encryptedToken = encrypt(it)
            cred.maskedToken = maskToken(it)
            cred.tokenFingerprint = fingerprint(it)
        }
        if (request.description != null) cred.description = request.description.takeIf { it.isNotBlank() }
        request.permissions?.let {
            cred.permissions.clear()
            cred.permissions.addAll(it.map(String::trim).filter(String::isNotEmpty))
        }
        cred.updatedAt = LocalDateTime.now()

        val saved = credentialRepository.save(cred)
        auditService?.logCredentialAction("UPDATE", WORKSPACE_OWNER_ID, cred.providerId, credentialId, true)
        return saved
    }
    
    /**
     * Retrieve decrypted credential by ID.
     * Performs RBAC check and updates lastUsedAt timestamp.
     * 
     * @param credentialId UUID of credential
     * @param userId User ID from security context (must be owner)
     * @param userRole User role for RBAC check
     * @return Decrypted credential token
     * @throws IllegalArgumentException if credential not found or access denied
     */
    fun retrieveCredential(credentialId: String, userId: String, userRole: String = "USER"): String {
        val cred = credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .or { credentialRepository.findByIdAndOwnerId(credentialId, WORKSPACE_OWNER_ID) }
            .orElseThrow { IllegalArgumentException("Credential not found: $credentialId") }
        
        // RBAC check
        if (!cred.isAccessibleBy(userId, userRole)) {
            auditService?.logCredentialAction("RETRIEVE_DENIED", userId, cred.providerId, credentialId, false)
            throw IllegalArgumentException("Access denied to credential: $credentialId")
        }
        
        // Decrypt and update usage
        val decrypted = decrypt(cred.encryptedToken)
        cred.updateLastUsed()
        credentialRepository.save(cred)
        
        auditService?.logCredentialAction("RETRIEVE", userId, cred.providerId, credentialId, true)
        
        return decrypted
    }
    
    /**
     * Resolve environment variable references in credential references.
     * Example: "$MY_API_KEY" → resolves to System.getenv("MY_API_KEY")
     * 
     * @param credentialRef Credential reference (UUID or $ENV_VAR format)
     * @param userId User ID for vault access
     * @param userRole User role for RBAC
     * @return Resolved credential token
     */
    fun resolveCredentialRef(credentialRef: String, userId: String, userRole: String = "USER"): String {
        val normalizedRef = credentialRef.removePrefix("@secret:").trim()
        return when {
            normalizedRef.startsWith("$") -> {
                // Environment variable reference
                val envVarName = normalizedRef.substring(1)
                System.getenv(envVarName)
                    ?: throw IllegalArgumentException("Environment variable not found: $envVarName")
            }
            else -> {
                // UUID or stored credential reference to vault
                try {
                    retrieveCredential(normalizedRef, userId, userRole)
                } catch (e: IllegalArgumentException) {
                    val namedCredential = credentialRepository.findByOwnerIdAndSecretName(userId, normalizedRef)
                        .or { credentialRepository.findByOwnerIdAndSecretName(WORKSPACE_OWNER_ID, normalizedRef) }
                    if (namedCredential.isPresent) {
                        return retrieveCredential(namedCredential.get().id, namedCredential.get().ownerId, userRole)
                    }
                    throw IllegalArgumentException("Credential not found for provided reference. Use a stored credentialId, a workspace secret name, or an environment variable reference like '\$AZURE_OPENAI_API_KEY'.")
                }
            }
        }
    }
    
    /**
     * Retrieve credential with full response DTO (for API responses).
     * 
     * @param credentialId UUID of credential
     * @param userId User ID (must be owner)
     * @return Credential DTO with masked token (last 4 chars only)
     */
    fun getCredentialDto(credentialId: String, userId: String): AICredentialResponseDto {
        val cred = credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow { IllegalArgumentException("Credential not found: $credentialId") }
        return AICredentialResponseDto(
            id = cred.id,
            name = cred.secretName,
            providerId = cred.providerId,
            credentialType = cred.credentialType,
            maskedToken = displayMaskedToken(cred),
            reference = "@secret:${cred.secretName}",
            description = cred.description,
            createdAt = cred.createdAt.toString(),
            updatedAt = cred.updatedAt.toString(),
            lastUsedAt = cred.lastUsedAt?.toString(),
            permissions = cred.permissions.toList()
        )
    }
    
    /**
     * List all credentials for a user (responses will be masked).
     * 
     * @param userId User ID
     * @return List of credential DTOs (tokens masked)
     */
    fun listCredentials(userId: String): List<AICredentialResponseDto> {
        return credentialRepository.findByOwnerIdOrderByCreatedAtDesc(userId).map(::toDto)
    }

    fun listAvailableCredentials(userId: String): List<AICredentialResponseDto> {
        val owned = credentialRepository.findByOwnerIdAndIsActiveOrderByCreatedAtDesc(userId, true)
        val workspace = credentialRepository.findByOwnerIdAndIsActiveOrderByCreatedAtDesc(WORKSPACE_OWNER_ID, true)
        return (owned + workspace).distinctBy { it.id }.map(::toDto)
    }

    fun listWorkspaceSecrets(): List<AICredentialResponseDto> =
        credentialRepository.findByOwnerIdOrderByCreatedAtDesc(WORKSPACE_OWNER_ID).map(::toDto)
    
    /**
     * Delete credential (soft delete for audit trail).
     * 
     * @param credentialId UUID of credential
     * @param userId User ID (must be owner)
     */
    fun deleteCredential(credentialId: String, userId: String) {
        val cred = credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .orElseThrow { IllegalArgumentException("Credential not found: $credentialId") }
        cred.deactivate()
        credentialRepository.save(cred)
        
        auditService?.logCredentialAction("DELETE", userId, cred.providerId, credentialId, true)
    }

    fun deleteWorkspaceSecret(credentialId: String) {
        deleteCredential(credentialId, WORKSPACE_OWNER_ID)
    }
    
    /**
     * Mask a credential token for safe display.
     * Shows last 4 characters only, hides the rest with asterisks.
     * Example: "sk-abc123def456" → "sk-***...def456"
     * 
     * @param token Raw token
     * @return Masked token
     */
    private fun maskToken(token: String): String {
        return if (token.length <= 4) {
            "****"
        } else {
            val lastFour = token.takeLast(4)
            "${token.take(4)}***...$lastFour"
        }
    }
    
    /**
     * Check if credential is valid (not expired, still active).
     * 
     * @param credentialId UUID of credential
     * @param userId User ID
     * @return true if credential exists and is active
     */
    fun isCredentialValid(credentialId: String, userId: String): Boolean {
        return credentialRepository.findByIdAndOwnerId(credentialId, userId)
            .or { credentialRepository.findByIdAndOwnerId(credentialId, WORKSPACE_OWNER_ID) }
            .map { it.isActive }
            .orElse(false)
    }

    private fun toDto(cred: AICredential): AICredentialResponseDto =
        AICredentialResponseDto(
            id = cred.id,
            name = cred.secretName,
            providerId = cred.providerId,
            credentialType = cred.credentialType,
            maskedToken = displayMaskedToken(cred),
            reference = "@secret:${cred.secretName}",
            description = cred.description,
            createdAt = cred.createdAt.toString(),
            updatedAt = cred.updatedAt.toString(),
            lastUsedAt = cred.lastUsedAt?.toString(),
            permissions = cred.permissions.toList()
        )

    private fun normalizeSecretName(value: String): String {
        val trimmed = value.trim().removePrefix("@secret:")
        require(trimmed.matches(Regex("[A-Za-z][A-Za-z0-9_-]{1,99}"))) {
            "Secret name must start with a letter and contain only letters, numbers, underscores, or hyphens."
        }
        return trimmed
    }

    private fun decryptGcm(encodedPayload: String): String {
        val payload = Base64.getDecoder().decode(encodedPayload)
        require(payload.size > AES_GCM_IV_BYTES) { "Invalid encrypted credential payload" }
        val iv = payload.copyOfRange(0, AES_GCM_IV_BYTES)
        val encryptedBytes = payload.copyOfRange(AES_GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }

    private fun decryptLegacyAes(ciphertext: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey)
        val decodedBytes = Base64.getDecoder().decode(ciphertext)
        return String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
    }

    private fun displayMaskedToken(cred: AICredential): String =
        cred.maskedToken.takeIf { it.isNotBlank() && it != "****" }
            ?: maskToken(decrypt(cred.encryptedToken))

    private fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun isProductionRuntime(): Boolean {
        val activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE").orEmpty()
        val appEnv = System.getenv("EASY_BPM_ENV").orEmpty()
        return listOf(activeProfiles, appEnv).any { value ->
            value.split(',', ';', ' ').any { it.equals("prod", ignoreCase = true) || it.equals("production", ignoreCase = true) }
        }
    }
}

/**
 * Optional audit service for logging credential access.
 * Implement this interface in your project to enable audit logging.
 */
interface AuditService {
    fun logCredentialAction(
        action: String,
        userId: String,
        providerId: String,
        credentialId: String? = null,
        success: Boolean = true
    )
}
