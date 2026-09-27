package com.easy.bpm.service.admin

import com.easy.bpm.dto.security.*
import com.easy.bpm.model.security.*
import com.easy.bpm.repository.security.ApiClientAuditRepository
import com.easy.bpm.repository.security.ApiClientRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.security.AppPermissions
import com.easy.bpm.security.AuthenticatedUser
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.criteria.Predicate
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID
import org.slf4j.LoggerFactory

data class ApiClientIdentity(
    val id: UUID,
    val name: String,
    val permissionCodes: Set<String>,
    val credentialGeneration: Int
)

@Service
class ApiClientService(
    private val clientRepository: ApiClientRepository,
    private val auditRepository: ApiClientAuditRepository,
    private val permissionRepository: PermissionRepository,
    private val objectMapper: ObjectMapper,
    private val properties: ApiClientProperties
) {
    private val random = SecureRandom()
    private val encoder = BCryptPasswordEncoder(12)
    private val dummyHash = encoder.encode(UUID.randomUUID().toString())
    private val logger = LoggerFactory.getLogger(ApiClientService::class.java)

    @Transactional
    fun create(request: CreateApiClientRequest, actor: AuthenticatedUser, requestId: String = UUID.randomUUID().toString()): ApiClientCredentialResponse {
        val now = LocalDateTime.now()
        val displayName = validateAndDisplayName(request.name)
        val normalizedName = normalizeName(displayName)
        val expiry = validateExpiry(request.expiresAt ?: now.plusDays(90), now)
        val permissions = resolveDelegatedPermissions(request.permissionCodes, actor)
        if (clientRepository.existsByNormalizedName(normalizedName)) conflict("API_CLIENT_NAME_EXISTS", "An API client with this name already exists")
        val credential = newCredential()
        var client = ApiClient(
            selector = credential.selector,
            normalizedName = normalizedName,
            name = displayName,
            description = cleanDescription(request.description),
            secretHash = encoder.encode(credential.secret),
            expiresAt = expiry,
            createdBy = actor.username,
            updatedBy = actor.username,
            permissions = permissions.toMutableSet()
        )
        try {
            client = clientRepository.saveAndFlush(client)
        } catch (_: DataIntegrityViolationException) {
            conflict("API_CLIENT_NAME_EXISTS", "An API client with this name already exists")
        }
        val changed = linkedSetOf("name", "credential", "status", "expiresAt")
        if (client.description != null) changed += "description"
        if (client.permissions.isNotEmpty()) changed += "permissions"
        saveLifecycleAudit(client, "CREATED", actor.username, changed, requestId)
        return ApiClientCredentialResponse(toResponse(client, now), credential.full)
    }

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, page: Int, size: Int): Page<ApiClientResponse> {
        val safeSize = size.coerceIn(1, 100)
        val safePage = page.coerceAtLeast(0)
        val now = LocalDateTime.now()
        val wantedStatus = status?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        if (wantedStatus != null && wantedStatus !in setOf("ACTIVE", "EXPIRED", "REVOKED")) {
            badRequest("INVALID_STATUS", "Status must be ACTIVE, EXPIRED, or REVOKED", mapOf("status" to "Invalid status"))
        }
        val spec = org.springframework.data.jpa.domain.Specification<ApiClient> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            q?.trim()?.takeIf { it.isNotEmpty() }?.let {
                predicates += cb.like(cb.lower(root.get("name")), "%${it.lowercase(Locale.ROOT)}%")
            }
            when (wantedStatus) {
                "REVOKED" -> predicates += cb.equal(root.get<ApiClientLifecycleStatus>("lifecycleStatus"), ApiClientLifecycleStatus.REVOKED)
                "EXPIRED" -> {
                    predicates += cb.equal(root.get<ApiClientLifecycleStatus>("lifecycleStatus"), ApiClientLifecycleStatus.ACTIVE)
                    predicates += cb.lessThanOrEqualTo(root.get("expiresAt"), now)
                }
                "ACTIVE" -> {
                    predicates += cb.equal(root.get<ApiClientLifecycleStatus>("lifecycleStatus"), ApiClientLifecycleStatus.ACTIVE)
                    predicates += cb.greaterThan(root.get("expiresAt"), now)
                }
            }
            cb.and(*predicates.toTypedArray())
        }
        return clientRepository.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map { toResponse(it, now) }
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ApiClientResponse = toResponse(find(id), LocalDateTime.now())

    @Transactional(readOnly = true)
    fun audit(id: UUID, category: String?, page: Int, size: Int): Page<ApiClientAuditResponse> {
        find(id)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = category?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val parsed = runCatching { ApiClientAuditCategory.valueOf(it.uppercase(Locale.ROOT)) }.getOrNull()
                ?: badRequest("INVALID_AUDIT_CATEGORY", "Category must be LIFECYCLE or USE", mapOf("category" to "Invalid category"))
            auditRepository.findAllByApiClientIdAndCategory(id, parsed, pageable)
        } ?: auditRepository.findAllByApiClientId(id, pageable)
        return result.map(::toAuditResponse)
    }

    @Transactional(readOnly = true)
    fun assignablePermissions(actor: AuthenticatedUser): List<AssignablePermissionResponse> {
        val allowed = actor.permissionCodes - NON_DELEGABLE
        return permissionRepository.findAllByCodeIn(allowed).sortedBy { it.code }
            .map { AssignablePermissionResponse(it.code, it.name) }
    }

    @Transactional
    fun update(id: UUID, expectedVersion: Long, request: UpdateApiClientRequest, actor: AuthenticatedUser, requestId: String = UUID.randomUUID().toString()): ApiClientResponse {
        val client = findForUpdate(id)
        checkVersion(client, expectedVersion)
        ensureMutable(client, allowExpired = false)
        val changed = linkedSetOf<String>()
        request.name?.let {
            val display = validateAndDisplayName(it)
            if (normalizeName(display) != client.normalizedName) {
                conflict("API_CLIENT_NAME_IMMUTABLE", "The normalized API client name cannot be changed")
            }
            if (client.name != display) {
                client.name = display
                changed += "name"
            }
        }
        request.description?.let {
            val clean = cleanDescription(it)
            if (client.description != clean) {
                client.description = clean
                changed += "description"
            }
        }
        request.permissionCodes?.let {
            val resolved = resolveDelegatedPermissions(it, actor)
            if (client.permissions.map { permission -> permission.code }.toSet() != resolved.map { permission -> permission.code }.toSet()) {
                client.permissions = resolved.toMutableSet()
                changed += "permissions"
            }
        }
        request.expiresAt?.let {
            val validated = validateExpiry(it, LocalDateTime.now())
            if (client.expiresAt != validated) {
                client.expiresAt = validated
                changed += "expiresAt"
            }
        }
        if (changed.isNotEmpty()) {
            client.updatedAt = LocalDateTime.now()
            client.updatedBy = actor.username
            clientRepository.saveAndFlush(client)
        }
        saveLifecycleAudit(client, "UPDATED", actor.username, changed, requestId)
        return toResponse(client, LocalDateTime.now())
    }

    @Transactional
    fun rotate(id: UUID, expectedVersion: Long, request: RotateApiClientRequest?, actor: AuthenticatedUser, requestId: String = UUID.randomUUID().toString()): ApiClientCredentialResponse {
        val client = findForUpdate(id)
        checkVersion(client, expectedVersion)
        if (client.lifecycleStatus == ApiClientLifecycleStatus.REVOKED) conflict("API_CLIENT_REVOKED", "A revoked API client cannot be rotated")
        val now = LocalDateTime.now()
        val expiry = request?.expiresAt?.let { validateExpiry(it, now) }
            ?: client.expiresAt.takeIf { it.isAfter(now) }
            ?: now.plusDays(90)
        val oldExpiry = client.expiresAt
        val credential = newCredential()
        client.selector = credential.selector
        client.secretHash = encoder.encode(credential.secret)
        client.credentialGeneration += 1
        client.expiresAt = expiry
        client.updatedAt = now
        client.updatedBy = actor.username
        clientRepository.saveAndFlush(client)
        val changed = linkedSetOf("credential")
        if (oldExpiry != expiry) changed += "expiresAt"
        saveLifecycleAudit(client, "ROTATED", actor.username, changed, requestId)
        return ApiClientCredentialResponse(toResponse(client, now), credential.full)
    }

    @Transactional
    fun revoke(id: UUID, expectedVersion: Long, actor: AuthenticatedUser, requestId: String = UUID.randomUUID().toString()) {
        val client = findForUpdate(id)
        checkVersion(client, expectedVersion)
        if (client.lifecycleStatus == ApiClientLifecycleStatus.REVOKED) conflict("API_CLIENT_REVOKED", "The API client is already revoked")
        val now = LocalDateTime.now()
        client.lifecycleStatus = ApiClientLifecycleStatus.REVOKED
        client.revokedAt = now
        client.revokedBy = actor.username
        client.updatedAt = now
        client.updatedBy = actor.username
        clientRepository.saveAndFlush(client)
        saveLifecycleAudit(client, "REVOKED", actor.username, setOf("status"), requestId)
    }

    @Transactional
    fun authenticate(selector: String, secret: String): ApiClientIdentity? {
        val client = clientRepository.findBySelectorForAuthentication(selector)
        val matches = encoder.matches(secret, client?.secretHash ?: dummyHash)
        if (client == null || !matches || client.lifecycleStatus != ApiClientLifecycleStatus.ACTIVE || !client.expiresAt.isAfter(LocalDateTime.now())) return null
        return ApiClientIdentity(client.id, client.name, client.permissions.map { it.code }.toSet() - NON_DELEGABLE, client.credentialGeneration)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun beginUse(
        identity: ApiClientIdentity,
        remoteIp: String,
        method: String,
        path: String,
        requestId: String
    ): Long {
        val now = LocalDateTime.now()
        clientRepository.updateLastUsedMonotonically(identity.id, now, remoteIp.take(64))
        val reference = clientRepository.getReferenceById(identity.id)
        return auditRepository.saveAndFlush(
            ApiClientAudit(
                apiClient = reference,
                category = ApiClientAuditCategory.USE,
                action = "API_REQUEST",
                outcome = "STARTED",
                actor = "api-client:${identity.id}",
                credentialGeneration = identity.credentialGeneration,
                remoteIp = remoteIp.take(64),
                httpMethod = method.take(12),
                requestPath = path.take(1024),
                requestId = requestId.take(128)
            )
        ).id
    }

    fun completeUse(auditId: Long, status: Int, durationMs: Long) {
        val outcome = if (status < 400) "SUCCEEDED" else "FAILED"
        try {
            if (auditRepository.finalizeStarted(auditId, status, durationMs, outcome) == 0) {
                logger.warn("API client use audit {} was already finalized or missing", auditId)
            }
        } catch (primaryFailure: Exception) {
            try {
                auditRepository.finalizeStarted(auditId, status, durationMs, outcome)
                logger.warn("API client use audit {} used fallback finalization", auditId)
            } catch (fallbackFailure: Exception) {
                logger.error("API client use audit {} finalization failed; durable STARTED record retained for scheduled recovery", auditId)
            }
        }
    }

    @Scheduled(cron = "\${easybpm.api-clients.audit-retention-cron:0 15 2 * * *}")
    @Transactional
    fun purgeExpiredAudit() {
        val days = properties.auditRetentionDays.coerceIn(30, 3650)
        val ids = auditRepository.findIdsCreatedBefore(LocalDateTime.now().minusDays(days.toLong()), PageRequest.of(0, 5000))
            .content.map { it.id }
        if (ids.isNotEmpty()) auditRepository.deleteAllByIdInBatch(ids)
    }

    @Scheduled(fixedDelayString = "\${easybpm.api-clients.audit-finalization-recovery-ms:60000}")
    fun recoverIncompleteUseAudits() {
        val staleMinutes = properties.finalizationStaleMinutes.coerceIn(5, 1440)
        runCatching { auditRepository.finalizeStaleStarted(LocalDateTime.now().minusMinutes(staleMinutes), ApiClientAuditCategory.USE) }
            .onFailure { logger.error("API client use audit recovery failed; durable STARTED records remain pending") }
    }

    private fun resolveDelegatedPermissions(codes: Set<String>, actor: AuthenticatedUser): Set<Permission> {
        val allowed = actor.permissionCodes - NON_DELEGABLE
        if (!allowed.containsAll(codes) || !AppPermissions.all.containsAll(codes)) forbiddenDelegation()
        val permissions = if (codes.isEmpty()) emptySet() else permissionRepository.findAllByCodeIn(codes).toSet()
        if (permissions.size != codes.size) forbiddenDelegation()
        return permissions
    }

    private fun saveLifecycleAudit(client: ApiClient, action: String, actor: String, changed: Set<String>, requestId: String) {
        try {
            auditRepository.saveAndFlush(
                ApiClientAudit(
                    apiClient = client,
                    category = ApiClientAuditCategory.LIFECYCLE,
                    action = action,
                    outcome = "SUCCEEDED",
                    actor = actor,
                    credentialGeneration = client.credentialGeneration,
                    requestId = requestId,
                    changedFields = objectMapper.valueToTree(changed.sorted())
                )
            )
        } catch (_: Exception) {
            throw ApiClientException(HttpStatus.SERVICE_UNAVAILABLE, "API_CLIENT_AUDIT_UNAVAILABLE", "API client audit storage is unavailable")
        }
    }

    private fun validateAndDisplayName(raw: String): String {
        val display = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
        if (display.length !in 3..100) badRequest("INVALID_API_CLIENT", "API client validation failed", mapOf("name" to "Name must be 3 to 100 characters"))
        return display
    }

    private fun normalizeName(display: String) = display.lowercase(Locale.ROOT)

    private fun cleanDescription(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null && clean.length > 500) badRequest("INVALID_API_CLIENT", "API client validation failed", mapOf("description" to "Description must not exceed 500 characters"))
        return clean
    }

    private fun validateExpiry(value: LocalDateTime, now: LocalDateTime): LocalDateTime {
        val minimum = now.plusHours(1)
        val maximum = now.plusDays(365)
        if (value.isBefore(minimum) || value.isAfter(maximum)) {
            badRequest("INVALID_API_CLIENT", "API client validation failed", mapOf("expiresAt" to "Expiry must be between 1 hour and 365 days from now"))
        }
        return value.truncatedTo(ChronoUnit.SECONDS)
    }

    private fun newCredential(): GeneratedCredential {
        val selector = randomBytes(12)
        val secret = randomBytes(32)
        return GeneratedCredential(selector, secret, "ebpm_$selector.$secret")
    }

    private fun randomBytes(count: Int): String = ByteArray(count).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    private fun find(id: UUID) = clientRepository.findById(id).orElseThrow {
        ApiClientException(HttpStatus.NOT_FOUND, "API_CLIENT_NOT_FOUND", "API client not found")
    }

    private fun findForUpdate(id: UUID) = clientRepository.findByIdForUpdate(id)
        ?: throw ApiClientException(HttpStatus.NOT_FOUND, "API_CLIENT_NOT_FOUND", "API client not found")

    private fun checkVersion(client: ApiClient, expected: Long) {
        if (client.version != expected) conflict("API_CLIENT_VERSION_CONFLICT", "The API client was changed by another request")
    }

    private fun ensureMutable(client: ApiClient, allowExpired: Boolean) {
        if (client.lifecycleStatus == ApiClientLifecycleStatus.REVOKED) conflict("API_CLIENT_REVOKED", "A revoked API client cannot be changed")
        if (!allowExpired && !client.expiresAt.isAfter(LocalDateTime.now())) conflict("API_CLIENT_EXPIRED", "An expired API client can only be rotated or revoked")
    }

    private fun toResponse(client: ApiClient, now: LocalDateTime) = ApiClientResponse(
        id = client.id,
        name = client.name,
        description = client.description,
        status = when {
            client.lifecycleStatus == ApiClientLifecycleStatus.REVOKED -> "REVOKED"
            !client.expiresAt.isAfter(now) -> "EXPIRED"
            else -> "ACTIVE"
        },
        expiresAt = client.expiresAt,
        permissionCodes = client.permissions.map { it.code }.toSet(),
        credentialGeneration = client.credentialGeneration,
        lastUsedAt = client.lastUsedAt,
        lastUsedIp = client.lastUsedIp,
        revokedAt = client.revokedAt,
        revokedBy = client.revokedBy,
        createdAt = client.createdAt,
        createdBy = client.createdBy,
        updatedAt = client.updatedAt,
        updatedBy = client.updatedBy,
        version = client.version
    )

    private fun toAuditResponse(audit: ApiClientAudit) = ApiClientAuditResponse(
        audit.id, audit.category.name, audit.action, audit.outcome, audit.actor,
        audit.credentialGeneration, audit.remoteIp, audit.httpMethod, audit.requestPath,
        audit.httpStatus, audit.requestId, audit.durationMs, audit.changedFields, audit.createdAt
    )

    private fun badRequest(code: String, message: String, fields: Map<String, String> = emptyMap()): Nothing =
        throw ApiClientException(HttpStatus.BAD_REQUEST, code, message, fields)
    private fun conflict(code: String, message: String): Nothing = throw ApiClientException(HttpStatus.CONFLICT, code, message)
    private fun forbiddenDelegation(): Nothing = throw ApiClientException(HttpStatus.FORBIDDEN, "API_CLIENT_PERMISSION_FORBIDDEN", "One or more requested permissions cannot be delegated")

    private data class GeneratedCredential(val selector: String, val secret: String, val full: String)

    companion object {
        val NON_DELEGABLE = setOf(AppPermissions.VIEW_API_CLIENTS, AppPermissions.MANAGE_API_CLIENTS)
    }
}
