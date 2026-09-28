package com.easy.bpm.security

import com.easy.bpm.service.admin.ApiClientIdentity
import com.easy.bpm.service.admin.ApiClientService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class ApiClientAuthenticationFilterTest : FunSpec({
    val service = mockk<ApiClientService>(relaxed = true)
    val filter = ApiClientAuthenticationFilter(service, ObjectMapper())
    val selector = "abcdefghijklmnop"
    val secret = "a".repeat(43)
    val header = "Bearer ebpm_$selector.$secret"

    afterTest { SecurityContextHolder.clearContext() }

    test("valid native credential creates a service principal and preserves endpoint authorization") {
        val id = UUID.randomUUID()
        every { service.authenticate(selector, secret) } returns ApiClientIdentity(id, "ERP", setOf(ApiScopes.TASKS_READ), 2)
        every { service.beginUse(any(), any(), any(), any(), any()) } returns 44L
        val request = MockHttpServletRequest("GET", "/tasks").apply {
            addHeader("Authorization", header)
            addHeader("X-Correlation-ID", "ebpm_$selector.$secret")
            remoteAddr = "192.0.2.20"
        }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>()
        every { chain.doFilter(any(), any()) } answers {
            val principal = SecurityContextHolder.getContext().authentication.principal as AuthenticatedUser
            principal.userId shouldBe null
            principal.username shouldBe "api-client:$id"
            principal.identityType shouldBe "API_CLIENT"
            principal.permissionCodes shouldBe emptySet()
            principal.scopeCodes shouldBe setOf(ApiScopes.TASKS_READ)
            response.status = 200
        }

        filter.doFilter(request, response, chain)

        verify(exactly = 1) { chain.doFilter(request, response) }
        val requestId = slot<String>()
        verify { service.beginUse(any(), "192.0.2.20", "GET", "/tasks", capture(requestId)) }
        Regex("^[0-9a-f-]{36}$").matches(requestId.captured) shouldBe true
        requestId.captured.contains("ebpm_") shouldBe false
        response.getHeader("X-Correlation-ID") shouldBe requestId.captured
        verify { service.completeUse(44L, 200, any()) }
    }

    test("unknown native credential returns the same generic 401 and never reaches controllers") {
        every { service.authenticate(selector, secret) } returns null
        val request = MockHttpServletRequest("GET", "/tasks").apply { addHeader("Authorization", header) }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        response.status shouldBe 401
        response.contentAsString shouldBe "{\"status\":401,\"error\":\"Unauthorized\"}"
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    test("native service identities are always forbidden from API client administration") {
        every { service.authenticate(selector, secret) } returns ApiClientIdentity(UUID.randomUUID(), "ERP", setOf(ApiScopes.PROCESSES_READ), 1)
        every { service.beginUse(any(), any(), any(), any(), any()) } returns 45L
        val request = MockHttpServletRequest("GET", "/admin/api-clients").apply { addHeader("Authorization", header) }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        response.status shouldBe 403
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    test("native service identity cannot reach API client administration under a servlet context path") {
        every { service.authenticate(selector, secret) } returns ApiClientIdentity(UUID.randomUUID(), "ERP", setOf(ApiScopes.PROCESSES_READ), 1)
        every { service.beginUse(any(), any(), any(), any(), any()) } returns 46L
        val request = MockHttpServletRequest("GET", "/easybpm/admin/api-clients").apply {
            contextPath = "/easybpm"
            servletPath = "/admin/api-clients"
            addHeader("Authorization", header)
        }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        response.status shouldBe 403
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    test("request is rejected with 503 before dispatch when required use audit is unavailable") {
        every { service.authenticate(selector, secret) } returns ApiClientIdentity(UUID.randomUUID(), "ERP", emptySet(), 1)
        every { service.beginUse(any(), any(), any(), any(), any()) } throws IllegalStateException("database unavailable")
        val request = MockHttpServletRequest("POST", "/processes").apply { addHeader("Authorization", header) }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        response.status shouldBe 503
        response.contentAsString shouldBe "{\"status\":503,\"error\":\"Service Unavailable\"}"
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }
})
