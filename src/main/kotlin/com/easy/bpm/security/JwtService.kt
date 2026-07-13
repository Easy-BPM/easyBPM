package com.easy.bpm.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${easybpm.security.jwt-secret:ZWFzeWJwbS1kZWZhdWx0LXNlY3JldC1tdXN0LWJlLWNoYW5nZWQ=}")
    secretBase64: String,
    @Value("\${easybpm.security.jwt-expiration-ms:3600000}")
    private val expirationMs: Long
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64))

    fun generateToken(user: AuthenticatedUser): String {
        val now = Date()
        val expiresAt = Date(now.time + expirationMs)
        return Jwts.builder()
            .subject(user.username)
            .claim("uid", user.userId)
            .claim("tenant", user.tenantId)
            .claim("groups", user.groups.toList())
            .claim("perms", user.permissionCodes.toList())
            .issuedAt(now)
            .expiration(expiresAt)
            .signWith(key)
            .compact()
    }

    fun extractUsername(token: String): String? = extractAllClaims(token).subject

    fun extractTenant(token: String): String? = extractAllClaims(token)["tenant"] as? String

    fun isTokenValid(token: String): Boolean = try {
        extractAllClaims(token)
        true
    } catch (_: Exception) {
        false
    }

    private fun extractAllClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
}

