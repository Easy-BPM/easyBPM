package com.easy.bpm.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class OidcJwtAuthenticationConverter(
    private val oidcIdentityService: OidcIdentityService
) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(source: Jwt): AbstractAuthenticationToken {
        val principal = oidcIdentityService.loadOrProvision(source)
        return UsernamePasswordAuthenticationToken(principal, source.tokenValue, principal.authorities)
    }
}
