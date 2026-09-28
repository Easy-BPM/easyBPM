package com.easy.bpm.security

import com.easy.bpm.service.admin.ApiClientService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException

@Component
class ApiClientAuthenticationFilter(
    private val service: ApiClientService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !ApiClientCredential.isNativeBearer(request.getHeader("Authorization"))

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val parsed = ApiClientCredential.parse(request.getHeader("Authorization")) ?: return unauthorized(response)
        val identity = service.authenticate(parsed.first, parsed.second) ?: return unauthorized(response)
        val principal = AuthenticatedUser(
            userId = null,
            usernameValue = "api-client:${identity.id}",
            passwordValue = "{API_CLIENT}",
            enabledValue = true,
            groups = emptySet(),
            permissionCodes = emptySet(),
            scopeCodes = identity.scopes,
            identityProvider = "API_CLIENT",
            externalIdentityId = identity.id.toString(),
            displayName = identity.name,
            identityType = "API_CLIENT"
        )
        val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authentication

        val requestId = RequestCorrelation.id(request)
        response.setHeader(RequestCorrelation.HEADER, requestId)
        val started = System.nanoTime()
        val auditId = try {
            service.beginUse(
                identity = identity,
                remoteIp = request.remoteAddr ?: "",
                method = request.method,
                path = sanitizePath(applicationPath(request)),
                requestId = requestId
            )
        } catch (_: Exception) {
            response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            response.contentType = "application/json"
            response.writer.write(objectMapper.writeValueAsString(mapOf("status" to 503, "error" to "Service Unavailable")))
            return
        }

        var failureStatus: Int? = null
        try {
            if (!isApiClientEndpoint(applicationPath(request))) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write(objectMapper.writeValueAsString(mapOf("status" to 403, "error" to "Forbidden")))
            } else {
                chain.doFilter(request, response)
            }
        } catch (exception: Exception) {
            failureStatus = when (exception) {
                is AccessDeniedException -> HttpServletResponse.SC_FORBIDDEN
                is AuthenticationException -> HttpServletResponse.SC_UNAUTHORIZED
                else -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            }
            throw exception
        } finally {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            service.completeUse(auditId, failureStatus ?: response.status, durationMs)
        }
    }

    private fun unauthorized(response: HttpServletResponse) {
        SecurityContextHolder.clearContext()
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(mapOf("status" to 401, "error" to "Unauthorized")))
    }

    private fun sanitizePath(path: String): String = path
        .replace(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "{id}")
        .replace(Regex("/(?=\\d+(?:/|$))\\d+"), "/{id}")

    private fun applicationPath(request: HttpServletRequest): String =
        request.servletPath.takeIf { it.isNotBlank() }
            ?: request.requestURI.removePrefix(request.contextPath.orEmpty())

    private fun isApiClientEndpoint(path: String): Boolean =
        path == "/processes" || path.startsWith("/processes/") ||
            path == "/tasks" || path.startsWith("/tasks/") ||
            path == "/forms" || path.startsWith("/forms/") ||
            path == "/api/documents" || path.startsWith("/api/documents/")
}
