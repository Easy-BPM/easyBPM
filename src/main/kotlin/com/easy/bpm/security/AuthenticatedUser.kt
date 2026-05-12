package com.easy.bpm.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class AuthenticatedUser(
    val userId: Long,
    private val usernameValue: String,
    private val passwordValue: String,
    private val enabledValue: Boolean,
    val groups: Set<String>,
    val permissionCodes: Set<String>
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        permissionCodes.map { SimpleGrantedAuthority(it) }

    override fun getPassword(): String = passwordValue

    override fun getUsername(): String = usernameValue

    override fun isEnabled(): Boolean = enabledValue

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true
}

