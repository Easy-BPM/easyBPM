package com.easy.bpm.controller

import com.easy.bpm.ai.dto.AICredentialCreateRequestDto
import com.easy.bpm.ai.dto.AICredentialResponseDto
import com.easy.bpm.ai.dto.AICredentialUpdateRequestDto
import com.easy.bpm.ai.service.CredentialVault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/secrets")
class AdminSecretController(
    private val credentialVault: CredentialVault
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_SECRETS', 'MANAGE_SECRETS')")
    fun listSecrets(): ResponseEntity<List<AICredentialResponseDto>> =
        ResponseEntity.ok(credentialVault.listWorkspaceSecrets())

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_SECRETS')")
    fun createSecret(@RequestBody request: AICredentialCreateRequestDto): ResponseEntity<AICredentialResponseDto> {
        if ((request.name ?: request.providerId).isBlank() || request.token.isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        return try {
            val saved = credentialVault.storeWorkspaceSecret(request)
            ResponseEntity.status(HttpStatus.CREATED).body(credentialVault.getCredentialDto(saved.id, CredentialVault.WORKSPACE_OWNER_ID))
        } catch (error: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SECRETS')")
    fun updateSecret(
        @PathVariable id: String,
        @RequestBody request: AICredentialUpdateRequestDto
    ): ResponseEntity<AICredentialResponseDto> =
        try {
            val saved = credentialVault.updateWorkspaceSecret(id, request)
            ResponseEntity.ok(credentialVault.getCredentialDto(saved.id, CredentialVault.WORKSPACE_OWNER_ID))
        } catch (error: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SECRETS')")
    fun deleteSecret(@PathVariable id: String): ResponseEntity<Void> =
        try {
            credentialVault.deleteWorkspaceSecret(id)
            ResponseEntity.noContent().build()
        } catch (error: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
}
