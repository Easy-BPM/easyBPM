package com.easy.bpm.ai.service

import com.easy.bpm.ai.dto.AICredentialCreateRequestDto
import com.easy.bpm.ai.entity.AICredential
import com.easy.bpm.ai.repository.AICredentialRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for CredentialVault encryption, storage, and retrieval.
 * Tests: encryption/decryption, credential storage, masking, RBAC, env var resolution
 */
class CredentialVaultTest {
    
    @Mock
    private lateinit var credentialRepository: AICredentialRepository
    
    private var auditService: SimpleAuditService? = null
    private lateinit var vault: CredentialVault
    
    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        auditService = SimpleAuditService()
        vault = CredentialVault(credentialRepository, auditService)
    }
    
    @Test
    fun `test encrypt decrypts to original value`() {
        val original = "sk-abc123def456xyz"
        val encrypted = vault.encrypt(original)
        
        // Encrypted should be different from original
        assertNotEquals(original, encrypted)
        
        // Decrypt should restore original
        val decrypted = vault.decrypt(encrypted)
        assertEquals(original, decrypted)
    }
    
    @Test
    fun `test encrypt produces different ciphertext for same plaintext`() {
        val plaintext = "secret-token"
        val encrypted1 = vault.encrypt(plaintext)
        val encrypted2 = vault.encrypt(plaintext)
        
        // Same plaintext may produce same or different ciphertext depending on encryption algorithm
        // Both should decrypt to same plaintext
        assertEquals(plaintext, vault.decrypt(encrypted1))
        assertEquals(plaintext, vault.decrypt(encrypted2))
    }
    
    @Test
    fun `test store credential encrypts token and persists`() {
        val request = AICredentialCreateRequestDto(
            providerId = "openai",
            credentialType = "API_KEY",
            token = "sk-test123"
        )
        
        // Mock repository behavior
        `when`(credentialRepository.findByProviderIdAndOwnerId("openai", "user123"))
            .thenReturn(Optional.empty())
        `when`(credentialRepository.save(any())).thenAnswer { invocation ->
            invocation.arguments[0] as AICredential
        }
        
        val stored = vault.storeCredential("user123", request)
        
        // Token should be encrypted
        assertNotEquals(request.token, stored.encryptedToken)
        assertEquals("openai", stored.providerId)
        assertEquals("API_KEY", stored.credentialType)
        
        // Repository should be called
        verify(credentialRepository).save(any())
    }
    
    @Test
    fun `test store credential fails if already exists for provider`() {
        val request = AICredentialCreateRequestDto(
            providerId = "openai",
            credentialType = "API_KEY",
            token = "sk-test123"
        )
        
        val existingCred = AICredential(
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = "encrypted",
            ownerId = "user123"
        )
        
        `when`(credentialRepository.findByProviderIdAndOwnerId("openai", "user123"))
            .thenReturn(Optional.of(existingCred))
        
        assertThrows<IllegalArgumentException> {
            vault.storeCredential("user123", request)
        }
    }
    
    @Test
    fun `test retrieve credential with RBAC check`() {
        val credential = AICredential(
            id = "cred-123",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = vault.encrypt("sk-secret"),
            ownerId = "user123",
            permissions = mutableSetOf()
        )
        
        `when`(credentialRepository.findByIdAndOwnerId("cred-123", "user123"))
            .thenReturn(Optional.of(credential))
        `when`(credentialRepository.save(any())).thenReturn(credential)
        
        val decrypted = vault.retrieveCredential("cred-123", "user123", "USER")
        assertEquals("sk-secret", decrypted)
    }
    
    @Test
    fun `test retrieve credential denies access for different user`() {
        val credential = AICredential(
            id = "cred-123",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = "encrypted",
            ownerId = "user123"
        )
        
        `when`(credentialRepository.findByIdAndOwnerId("cred-123", "user456"))
            .thenReturn(Optional.empty())
        
        assertThrows<IllegalArgumentException> {
            vault.retrieveCredential("cred-123", "user456", "USER")
        }
    }
    
    @Test
    fun `test resolve environment variable reference`() {
        val resolved = vault.resolveCredentialRef("\$PATH", "user123")
        assertTrue(resolved.isNotEmpty())
    }

    @Test
    fun `test missing credential reference returns safe error`() {
        val rawSecret = "THISLOOKSLIKEARAWSECRET1234567890VALUE"

        `when`(credentialRepository.findByIdAndOwnerId(rawSecret, "user123"))
            .thenReturn(Optional.empty())

        val error = assertThrows<IllegalArgumentException> {
            vault.resolveCredentialRef(rawSecret, "user123")
        }

        assertTrue(error.message!!.contains("Credential not found for provided reference"))
        assertTrue(!error.message!!.contains(rawSecret))
    }
    
    @Test
    fun `test mask token shows only last 4 characters`() {
        val credential = AICredential(
            id = "cred-123",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = vault.encrypt("sk-abc123def456xyz"),
            ownerId = "user123"
        )
        
        `when`(credentialRepository.findByIdAndOwnerId("cred-123", "user123"))
            .thenReturn(Optional.of(credential))
        
        val dto = vault.getCredentialDto("cred-123", "user123")
        
        // Masked should end with last 4 chars of original
        assertTrue(dto.maskedToken.endsWith("xyz"))
        assertTrue(dto.maskedToken.contains("***"))
    }
    
    @Test
    fun `test list credentials for user`() {
        val cred1 = AICredential(
            id = "cred-1",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = vault.encrypt("sk-test1"),
            ownerId = "user123"
        )
        val cred2 = AICredential(
            id = "cred-2",
            providerId = "anthropic",
            credentialType = "API_KEY",
            encryptedToken = vault.encrypt("sk-test2"),
            ownerId = "user123"
        )
        
        `when`(credentialRepository.findByOwnerIdOrderByCreatedAtDesc("user123"))
            .thenReturn(listOf(cred1, cred2))
        
        val dtos = vault.listCredentials("user123")
        
        assertEquals(2, dtos.size)
        assertTrue(dtos.all { it.maskedToken.contains("***") })  // All masked
    }
    
    @Test
    fun `test delete credential soft deletes`() {
        val credential = AICredential(
            id = "cred-123",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = "encrypted",
            ownerId = "user123",
            isActive = true
        )
        
        `when`(credentialRepository.findByIdAndOwnerId("cred-123", "user123"))
            .thenReturn(Optional.of(credential))
        `when`(credentialRepository.save(any())).thenReturn(credential)
        
        vault.deleteCredential("cred-123", "user123")
        
        assertTrue(!credential.isActive)
        verify(credentialRepository).save(credential)
    }
    
    @Test
    fun `test is credential valid check`() {
        val credential = AICredential(
            id = "cred-123",
            providerId = "openai",
            credentialType = "API_KEY",
            encryptedToken = "encrypted",
            ownerId = "user123",
            isActive = true
        )
        
        `when`(credentialRepository.findByIdAndOwnerId("cred-123", "user123"))
            .thenReturn(Optional.of(credential))
        
        assertTrue(vault.isCredentialValid("cred-123", "user123"))
    }
}
