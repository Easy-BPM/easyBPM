package com.easy.bpm.security

import com.easy.bpm.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: AppUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authHeader = request.getHeader("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                TenantContext.setTenant(request.getHeader(TenantContext.TENANT_HEADER))
                filterChain.doFilter(request, response)
                return
            }

            val jwt = authHeader.substring(7)
            val username = jwtService.extractUsername(jwt)
            val tenantFromToken = jwtService.extractTenant(jwt)
            TenantContext.setTenant(tenantFromToken ?: request.getHeader(TenantContext.TENANT_HEADER))

            if (username != null && SecurityContextHolder.getContext().authentication == null && jwtService.isTokenValid(jwt)) {
                val userDetails = userDetailsService.loadUserByUsername(username)
                val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }

            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }
}
