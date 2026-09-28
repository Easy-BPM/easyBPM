package com.easy.bpm.controller

import com.easy.bpm.ai.dto.AICredentialResponseDto
import com.easy.bpm.ai.service.CredentialVault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only runtime-secret catalog shared by API Tasks, AI Tasks, and other
 * modeler integrations. Secret values are never returned by this resource.
 */
@RestController
@RequestMapping("/secrets")
class RuntimeSecretController(
    private val credentialVault: CredentialVault
) {
    @GetMapping("/available")
    @PreAuthorize("hasAnyAuthority('ACCESS_BPM_ADMIN', 'ACCESS_BPM_MODELER')")
    fun listAvailableSecrets(): ResponseEntity<List<AICredentialResponseDto>> =
        ResponseEntity.ok(credentialVault.listWorkspaceSecrets())
}
