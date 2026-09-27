package com.easy.bpm.controller

import com.easy.bpm.dto.security.*
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.service.admin.ApiClientException
import com.easy.bpm.service.admin.ApiClientService
import org.springframework.data.domain.Page
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID
import jakarta.servlet.http.HttpServletRequest
import com.easy.bpm.security.RequestCorrelation

@RestController
@RequestMapping("/admin/api-clients")
class ApiClientAdminController(private val service: ApiClientService) {
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_API_CLIENTS')")
    fun create(
        @RequestBody request: CreateApiClientRequest,
        @AuthenticationPrincipal actor: AuthenticatedUser,
        servletRequest: HttpServletRequest
    ): ResponseEntity<ApiClientCredentialResponse> {
        val created = service.create(request, actor, RequestCorrelation.id(servletRequest))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.client.id).toUri()
        return ResponseEntity.created(location).eTag(quoted(created.client.version)).body(created)
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_API_CLIENTS', 'MANAGE_API_CLIENTS')")
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): Page<ApiClientResponse> = service.list(q, status, page, size)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_API_CLIENTS', 'MANAGE_API_CLIENTS')")
    fun get(@PathVariable id: UUID): ResponseEntity<ApiClientResponse> {
        val result = service.get(id)
        return ResponseEntity.ok().eTag(quoted(result.version)).body(result)
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyAuthority('VIEW_API_CLIENTS', 'MANAGE_API_CLIENTS')")
    fun audit(
        @PathVariable id: UUID,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): Page<ApiClientAuditResponse> = service.audit(id, category, page, size)

    @GetMapping("/assignable-permissions")
    @PreAuthorize("hasAuthority('MANAGE_API_CLIENTS')")
    fun assignable(@AuthenticationPrincipal actor: AuthenticatedUser): List<AssignablePermissionResponse> =
        service.assignablePermissions(actor)

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_API_CLIENTS')")
    fun update(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: UpdateApiClientRequest,
        @AuthenticationPrincipal actor: AuthenticatedUser,
        servletRequest: HttpServletRequest
    ): ResponseEntity<ApiClientResponse> {
        val result = service.update(id, requireVersion(ifMatch), request, actor, RequestCorrelation.id(servletRequest))
        return ResponseEntity.ok().eTag(quoted(result.version)).body(result)
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('MANAGE_API_CLIENTS')")
    fun rotate(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody(required = false) request: RotateApiClientRequest?,
        @AuthenticationPrincipal actor: AuthenticatedUser,
        servletRequest: HttpServletRequest
    ): ResponseEntity<ApiClientCredentialResponse> {
        val result = service.rotate(id, requireVersion(ifMatch), request, actor, RequestCorrelation.id(servletRequest))
        return ResponseEntity.ok().eTag(quoted(result.client.version)).body(result)
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('MANAGE_API_CLIENTS')")
    fun revoke(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @AuthenticationPrincipal actor: AuthenticatedUser,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        service.revoke(id, requireVersion(ifMatch), actor, RequestCorrelation.id(servletRequest))
        return ResponseEntity.noContent().build()
    }

    private fun requireVersion(value: String?): Long {
        if (value == null) throw ApiClientException(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED", "A quoted numeric If-Match header is required")
        return Regex("^\"(\\d+)\"$").matchEntire(value)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw ApiClientException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH", "If-Match must be a quoted numeric version")
    }

    private fun quoted(version: Long) = "\"$version\""
}
