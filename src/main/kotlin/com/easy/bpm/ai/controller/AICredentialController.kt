package com.easy.bpm.ai.controller

import com.easy.bpm.ai.dto.AICredentialCreateRequestDto
import com.easy.bpm.ai.dto.AICredentialResponseDto
import com.easy.bpm.ai.service.CredentialVault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * REST controller for AI credential management.
 * Endpoints:
 * - POST   /ai/credentials              (Create credential)
 * - GET    /ai/credentials              (List user's credentials)
 * - GET    /ai/credentials/{id}         (Get specific credential)
 * - DELETE /ai/credentials/{id}         (Delete credential)
 */
@RestController
@RequestMapping("/ai/credentials")
class AICredentialController(
    private val credentialVault: CredentialVault
) {
    
    /**
     * Create a new AI credential.
     * Token is encrypted immediately upon storage.
     * 
     * POST /ai/credentials
     * Content-Type: application/json
     * 
     * Request body:
     * {
     *   "providerId": "openai",
     *   "credentialType": "API_KEY",
     *   "token": "sk-abc123def456xyz"
     * }
     * 
     * Response: 201 Created
     * {
     *   "id": "uuid-1234",
     *   "providerId": "openai",
     *   "credentialType": "API_KEY",
     *   "maskedToken": "sk-***...xyz",
     *   "createdAt": "2026-05-22T13:31:27Z",
     *   "updatedAt": "2026-05-22T13:31:27Z",
     *   "lastUsedAt": null,
     *   "permissions": []
     * }
     */
    @PostMapping
    fun createCredential(
        @RequestBody request: AICredentialCreateRequestDto,
        authentication: Authentication
    ): ResponseEntity<AICredentialResponseDto> {
        val userId = authentication.name
        
        if (request.token.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        
        return try {
            val credential = credentialVault.storeCredential(userId, request)
            val dto = credentialVault.getCredentialDto(credential.id, userId)
            ResponseEntity.status(HttpStatus.CREATED).body(dto)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
    
    /**
     * List all credentials for the authenticated user.
     * Tokens are masked in response.
     * 
     * GET /ai/credentials
     * 
     * Response: 200 OK
     * [
     *   {
     *     "id": "uuid-1234",
     *     "providerId": "openai",
     *     "credentialType": "API_KEY",
     *     "maskedToken": "sk-***...xyz",
     *     ...
     *   }
     * ]
     */
    @GetMapping
    fun listCredentials(
        authentication: Authentication
    ): ResponseEntity<List<AICredentialResponseDto>> {
        val userId = authentication.name
        val credentials = credentialVault.listCredentials(userId)
        return ResponseEntity.ok(credentials)
    }
    
    /**
     * Get a specific credential by ID.
     * Performs ownership check to prevent unauthorized access.
     * 
     * GET /ai/credentials/{id}
     * 
     * Response: 200 OK
     * {
     *   "id": "uuid-1234",
     *   "providerId": "openai",
     *   "credentialType": "API_KEY",
     *   "maskedToken": "sk-***...xyz",
     *   ...
     * }
     */
    @GetMapping("/{id}")
    fun getCredential(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<AICredentialResponseDto> {
        val userId = authentication.name
        
        return try {
            val dto = credentialVault.getCredentialDto(id, userId)
            ResponseEntity.ok(dto)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Delete a credential (soft delete for audit trail).
     * 
     * DELETE /ai/credentials/{id}
     * 
     * Response: 204 No Content
     */
    @DeleteMapping("/{id}")
    fun deleteCredential(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val userId = authentication.name
        
        return try {
            credentialVault.deleteCredential(id, userId)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Check if a credential is valid (exists and is active).
     * Used by frontend for form validation and UI hints.
     * 
     * GET /ai/credentials/{id}/valid
     * 
     * Response: 200 OK
     * { "valid": true }
     */
    @GetMapping("/{id}/valid")
    fun isCredentialValid(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Boolean>> {
        val userId = authentication.name
        val valid = credentialVault.isCredentialValid(id, userId)
        return ResponseEntity.ok(mapOf("valid" to valid))
    }
}
