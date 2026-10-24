package com.easy.bpm.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest : FunSpec({
    val secret = "ZWFzeWJwbS1kZWZhdWx0LXNlY3JldC1tdXN0LWJlLWNoYW5nZWQ="
    val principal = AuthenticatedUser(1L, "test-user", "", true, emptySet(), setOf("ACCESS_PROCESS_PORTAL"))
    afterTest { SecurityContextHolder.clearContext() }

    listOf("expired", "malformed", "bad-signature", "valid").forEach { scenario ->
        test("$scenario JWT preserves authentication boundary without throwing") {
            val service = JwtService(secret, if (scenario == "expired") -60000 else 60000)
            val token = when (scenario) {
                "malformed" -> "not-a-jwt"
                "bad-signature" -> JwtService("YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=", 60000).generateToken(principal)
                else -> service.generateToken(principal)
            }
            val users = mockk<AppUserDetailsService>()
            every { users.loadUserByUsername("test-user") } returns principal
            val filter = JwtAuthenticationFilter(service, users, ExternalAuthenticationProperties())
            val request = MockHttpServletRequest("GET", "/tasks/sla/summary").apply {
                addHeader("Authorization", "Bearer $token")
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>()
            every { chain.doFilter(any(), any()) } answers {
                response.status = if (SecurityContextHolder.getContext().authentication == null) 401 else 200
            }
            filter.doFilter(request, response, chain)
            response.status shouldBe if (scenario == "valid") 200 else 401
            verify(exactly = if (scenario == "valid") 1 else 0) { users.loadUserByUsername(any()) }
            verify(exactly = 1) { chain.doFilter(request, response) }
        }
    }
})