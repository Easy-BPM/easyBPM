package com.easy.bpm.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

object RequestCorrelation {
    const val ATTRIBUTE = "easybpm.serverRequestId"
    const val HEADER = "X-Correlation-ID"

    fun id(request: HttpServletRequest): String =
        request.getAttribute(ATTRIBUTE)?.toString()?.takeIf { SAFE_UUID.matches(it) }
            ?: UUID.randomUUID().toString().also { request.setAttribute(ATTRIBUTE, it) }

    private val SAFE_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val requestId = RequestCorrelation.id(request)
        response.setHeader(RequestCorrelation.HEADER, requestId)
        filterChain.doFilter(request, response)
    }
}
