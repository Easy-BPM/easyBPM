package com.easy.bpm.service

import com.easy.bpm.dto.security.CurrentUserResponse
import com.easy.bpm.dto.security.LoginRequest
import com.easy.bpm.dto.security.LoginResponse
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.security.JwtService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import com.easy.bpm.tenant.TenantContext

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService
) {

    fun login(request: LoginRequest): LoginResponse {
        TenantContext.setTenant(request.tenantId)
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password)
            )
        } catch (_: Exception) {
            throw BadCredentialsException("Invalid username or password")
        }

        val principal = authentication.principal as AuthenticatedUser
        val token = jwtService.generateToken(principal)

        return LoginResponse(
            token = token,
            username = principal.username,
            tenantId = principal.tenantId,
            groups = principal.groups,
            permissions = principal.permissionCodes
        )
    }

    fun me(): CurrentUserResponse {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BadCredentialsException("Not authenticated")

        return CurrentUserResponse(
            username = principal.username,
            tenantId = principal.tenantId,
            groups = principal.groups,
            permissions = principal.permissionCodes
        )
    }
}

