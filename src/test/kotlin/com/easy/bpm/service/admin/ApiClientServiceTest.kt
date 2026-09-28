package com.easy.bpm.service.admin

import com.easy.bpm.dto.security.CreateApiClientRequest
import com.easy.bpm.dto.security.UpdateApiClientRequest
import com.easy.bpm.dto.security.RotateApiClientRequest
import com.easy.bpm.model.security.ApiClient
import com.easy.bpm.model.security.ApiClientAudit
import com.easy.bpm.repository.security.ApiClientAuditRepository
import com.easy.bpm.repository.security.ApiClientRepository
import com.easy.bpm.security.ApiScopes
import com.easy.bpm.security.AppPermissions
import com.easy.bpm.security.AuthenticatedUser
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.LocalDateTime
import java.util.UUID

class ApiClientServiceTest : FunSpec({
    lateinit var clients: ApiClientRepository
    lateinit var audits: ApiClientAuditRepository
    lateinit var service: ApiClientService
    val clientSlot = slot<ApiClient>()
    val auditSlot = slot<ApiClientAudit>()

    val actor = AuthenticatedUser(
        userId = 1,
        usernameValue = "manager",
        passwordValue = "ignored",
        enabledValue = true,
        groups = emptySet(),
        permissionCodes = setOf(AppPermissions.MANAGE_API_CLIENTS)
    )

    beforeTest {
        clients = mockk()
        audits = mockk()
        service = ApiClientService(clients, audits, ObjectMapper(), ApiClientProperties())
        every { clients.existsByNormalizedName(any()) } returns false
        every { clients.saveAndFlush(capture(clientSlot)) } answers { firstArg() }
        every { audits.saveAndFlush(capture(auditSlot)) } answers { firstArg() }
    }

    test("creates a one-time credential and stores only a bcrypt cost 12 hash") {
        val result = service.create(
            CreateApiClientRequest(
                name = "  Order   Connector  ",
                scopes = setOf(ApiScopes.PROCESSES_READ),
                expiresAt = LocalDateTime.now().plusDays(30)
            ),
            actor
        )

        result.credential shouldMatch Regex("^ebpm_[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{43}$")
        result.client.name shouldBe "Order Connector"
        result.client.scopes.shouldContainExactly(ApiScopes.PROCESSES_READ)
        val secret = result.credential.substringAfter('.')
        clientSlot.captured.secretHash.startsWith("$2") shouldBe true
        clientSlot.captured.secretHash.split('$')[2] shouldBe "12"
        BCryptPasswordEncoder(12).matches(secret, clientSlot.captured.secretHash) shouldBe true
        clientSlot.captured.secretHash.contains(secret) shouldBe false
    }

    test("rejects user permissions and unknown values as API scopes") {
        val exception = shouldThrow<ApiClientException> {
            service.create(
                CreateApiClientRequest(
                    name = "Privileged Connector",
                    scopes = setOf(AppPermissions.MANAGE_API_CLIENTS),
                    expiresAt = LocalDateTime.now().plusDays(30)
                ),
                actor
            )
        }
        exception.status shouldBe HttpStatus.BAD_REQUEST
        exception.code shouldBe "INVALID_API_CLIENT_SCOPE"
    }

    test("rejects expiry shorter than one hour") {
        val exception = shouldThrow<ApiClientException> {
            service.create(
                CreateApiClientRequest(name = "Short Client", expiresAt = LocalDateTime.now().plusMinutes(30)),
                actor
            )
        }
        exception.status shouldBe HttpStatus.BAD_REQUEST
        exception.fieldErrors.keys shouldContainExactly setOf("expiresAt")
    }

    test("rotation replaces the credential atomically while preserving identity and scopes") {
        val id = UUID.randomUUID()
        val oldSecret = "b".repeat(43)
        val client = ApiClient(
            id = id,
            selector = "oldselector00000",
            normalizedName = "erp connector",
            name = "ERP Connector",
            secretHash = BCryptPasswordEncoder(12).encode(oldSecret),
            expiresAt = LocalDateTime.now().plusDays(30),
            createdBy = "manager",
            updatedBy = "manager",
            scopes = mutableSetOf(ApiScopes.PROCESSES_READ)
        )
        every { clients.findByIdForUpdate(id) } returns client

        val rotated = service.rotate(id, 0, null, actor)

        rotated.client.id shouldBe id
        rotated.client.credentialGeneration shouldBe 2
        rotated.client.scopes.shouldContainExactly(ApiScopes.PROCESSES_READ)
        BCryptPasswordEncoder(12).matches(oldSecret, client.secretHash) shouldBe false
        BCryptPasswordEncoder(12).matches(rotated.credential.substringAfter('.'), client.secretHash) shouldBe true
    }

    test("revocation is terminal") {
        val id = UUID.randomUUID()
        val client = ApiClient(
            id = id,
            selector = "oldselector00000",
            normalizedName = "terminal client",
            name = "Terminal Client",
            secretHash = BCryptPasswordEncoder(12).encode("b".repeat(43)),
            expiresAt = LocalDateTime.now().plusDays(30),
            createdBy = "manager",
            updatedBy = "manager"
        )
        every { clients.findByIdForUpdate(id) } returns client

        val requestId = UUID.randomUUID().toString()
        service.revoke(id, 0, actor, requestId)
        client.lifecycleStatus.name shouldBe "REVOKED"
        auditSlot.captured.requestId shouldBe requestId
        auditSlot.captured.changedFields!!.map { it.asText() } shouldContainExactly listOf("status")

        val exception = shouldThrow<ApiClientException> { service.rotate(id, client.version, null, actor) }
        exception.status shouldBe HttpStatus.CONFLICT
    }

    test("stale lifecycle version is rejected") {
        val id = UUID.randomUUID()
        val client = ApiClient(
            id = id,
            selector = "oldselector00000",
            normalizedName = "versioned client",
            name = "Versioned Client",
            secretHash = BCryptPasswordEncoder(12).encode("b".repeat(43)),
            expiresAt = LocalDateTime.now().plusDays(30),
            createdBy = "manager",
            updatedBy = "manager",
            version = 4
        )
        every { clients.findByIdForUpdate(id) } returns client

        val exception = shouldThrow<ApiClientException> { service.revoke(id, 3, actor) }
        exception.status shouldBe HttpStatus.CONFLICT
        exception.code shouldBe "API_CLIENT_VERSION_CONFLICT"
    }

    test("authentication accepts a valid secret and rejects it at or after expiry") {
        val selector = "abcdefghijklmnop"
        val secret = "c".repeat(43)
        val client = ApiClient(
            selector = selector,
            normalizedName = "auth client",
            name = "Auth Client",
            secretHash = BCryptPasswordEncoder(12).encode(secret),
            expiresAt = LocalDateTime.now().plusHours(2),
            createdBy = "manager",
            updatedBy = "manager",
            scopes = mutableSetOf(ApiScopes.PROCESSES_READ, ApiScopes.TASKS_READ)
        )
        every { clients.findBySelectorForAuthentication(selector) } returns client

        val identity = service.authenticate(selector, secret)
        identity?.id shouldBe client.id
        identity?.scopes shouldContainExactly setOf(ApiScopes.PROCESSES_READ, ApiScopes.TASKS_READ)
        client.expiresAt = LocalDateTime.now()
        service.authenticate(selector, secret) shouldBe null
        client.expiresAt = LocalDateTime.now().minusSeconds(1)
        service.authenticate(selector, secret) shouldBe null
    }

    test("lifecycle audit has safe request id and actual no-op changed fields") {
        val id = UUID.randomUUID()
        val client = ApiClient(
            id = id, selector = "oldselector00000", normalizedName = "no op client", name = "No Op Client",
            secretHash = BCryptPasswordEncoder(12).encode("b".repeat(43)), expiresAt = LocalDateTime.now().plusDays(30),
            createdBy = "manager", updatedBy = "manager"
        )
        every { clients.findByIdForUpdate(id) } returns client
        val requestId = UUID.randomUUID().toString()

        service.update(id, 0, UpdateApiClientRequest(), actor, requestId)

        auditSlot.captured.requestId shouldBe requestId
        auditSlot.captured.changedFields!!.size() shouldBe 0
    }

    test("update and rotation audits contain only fields that actually changed") {
        val id = UUID.randomUUID()
        val expiry = LocalDateTime.now().plusDays(30).withNano(0)
        val client = ApiClient(
            id = id, selector = "oldselector00000", normalizedName = "diff client", name = "Diff Client",
            description = "old", secretHash = BCryptPasswordEncoder(12).encode("b".repeat(43)), expiresAt = expiry,
            createdBy = "manager", updatedBy = "manager"
        )
        every { clients.findByIdForUpdate(id) } returns client

        service.update(id, 0, UpdateApiClientRequest(description = "new"), actor, UUID.randomUUID().toString())
        auditSlot.captured.changedFields!!.map { it.asText() } shouldContainExactly listOf("description")

        service.rotate(id, 0, null, actor, UUID.randomUUID().toString())
        auditSlot.captured.changedFields!!.map { it.asText() } shouldContainExactly listOf("credential")

        service.rotate(id, 0, RotateApiClientRequest(LocalDateTime.now().plusDays(40)), actor, UUID.randomUUID().toString())
        auditSlot.captured.changedFields!!.map { it.asText() } shouldContainExactly listOf("credential", "expiresAt")
    }

    test("use audit finalization retries and durable stale recovery remains available") {
        every { audits.finalizeStarted(77L, 204, 12L, "SUCCEEDED") } throws IllegalStateException("transient") andThen 1
        every { audits.finalizeStaleStarted(any(), any()) } returns 1

        service.completeUse(77L, 204, 12L)
        service.recoverIncompleteUseAudits()

        verify(exactly = 2) { audits.finalizeStarted(77L, 204, 12L, "SUCCEEDED") }
        verify { audits.finalizeStaleStarted(any(), any()) }
    }

    test("use audit completion failure does not turn an executed endpoint into a retry response") {
        every { audits.finalizeStarted(88L, 201, 9L, "SUCCEEDED") } throws IllegalStateException("database unavailable")
        every { audits.finalizeStaleStarted(any(), any()) } returns 1

        shouldNotThrowAny { service.completeUse(88L, 201, 9L) }
        service.recoverIncompleteUseAudits()

        verify(exactly = 2) { audits.finalizeStarted(88L, 201, 9L, "SUCCEEDED") }
        verify { audits.finalizeStaleStarted(any(), any()) }
    }
})
