package com.easy.bpm.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import jakarta.servlet.FilterChain

class RequestCorrelationFilterTest : FunSpec({
    test("caller correlation header is never trusted or reflected") {
        val supplied = "ebpm_abcdefghijklmnop.${"s".repeat(43)}"
        val request = MockHttpServletRequest("GET", "/tasks").apply {
            addHeader(RequestCorrelation.HEADER, supplied)
        }
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>()
        every { chain.doFilter(request, response) } returns Unit

        RequestCorrelationFilter().doFilter(request, response, chain)

        val generated = response.getHeader(RequestCorrelation.HEADER)!!
        generated shouldBe RequestCorrelation.id(request)
        Regex("^[0-9a-f-]{36}$").matches(generated) shouldBe true
        generated.contains("ebpm_") shouldBe false
    }
})
