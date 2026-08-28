package com.easy.bpm.dto.security

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val username: String,
    val groups: Set<String>,
    val permissions: Set<String>
)

data class CurrentUserResponse(
    val id: Long,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val identityProvider: String = "LOCAL",
    val externalIdentityId: String? = null,
    val groups: Set<String>,
    val permissions: Set<String>
)

data class AuthProviderConfigResponse(
    val provider: String,
    val oidc: OidcAuthConfigResponse? = null
)

data class OidcAuthConfigResponse(
    val issuerUri: String,
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val logoutEndpoint: String
)

