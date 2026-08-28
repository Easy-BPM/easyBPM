package com.easy.bpm.service.auth

import com.easy.bpm.dto.security.AuthProviderConfigResponse
import com.easy.bpm.dto.security.CurrentUserResponse
import com.easy.bpm.dto.security.LoginRequest
import com.easy.bpm.dto.security.LoginResponse
import com.easy.bpm.dto.security.OidcAuthConfigResponse
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.security.ExternalAuthenticationProperties
import com.easy.bpm.security.JwtService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val authenticationProperties: ExternalAuthenticationProperties
) {

    fun login(request: LoginRequest): LoginResponse {
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
            groups = principal.groups,
            permissions = principal.permissionCodes
        )
    }

    fun me(): CurrentUserResponse {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BadCredentialsException("Not authenticated")

        return CurrentUserResponse(
            id = principal.userId,
            username = principal.username,
            email = principal.email,
            displayName = principal.displayName,
            identityProvider = principal.identityProvider,
            externalIdentityId = principal.externalIdentityId,
            groups = principal.groups,
            permissions = principal.permissionCodes
        )
    }

    fun config(): AuthProviderConfigResponse {
        if (!authenticationProperties.isOidcEnabled()) {
            return AuthProviderConfigResponse(provider = "local")
        }

        val issuerUri = authenticationProperties.oidc.issuerUri.trim().trimEnd('/')
        return AuthProviderConfigResponse(
            provider = authenticationProperties.provider.lowercase(),
            oidc = OidcAuthConfigResponse(
                issuerUri = issuerUri,
                clientId = authenticationProperties.oidc.clientId,
                authorizationEndpoint = "$issuerUri/protocol/openid-connect/auth",
                tokenEndpoint = "$issuerUri/protocol/openid-connect/token",
                logoutEndpoint = "$issuerUri/protocol/openid-connect/logout"
            )
        )
    }
}

