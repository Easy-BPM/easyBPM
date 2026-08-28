package com.easy.bpm.security

import com.easy.bpm.repository.security.AppUserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(
    private val appUserRepository: AppUserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = appUserRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User '$username' not found")

        val groupCodes = user.groups.map { it.code }.toSet()
        val permissionsFromGroups = user.groups.flatMap { group -> group.permissions.map { it.code } }.toSet()
        val directPermissions = user.permissions.map { it.code }.toSet()

        return AuthenticatedUser(
            userId = user.id,
            usernameValue = user.username,
            passwordValue = user.passwordHash,
            enabledValue = user.enabled,
            groups = groupCodes,
            permissionCodes = permissionsFromGroups + directPermissions,
            identityProvider = user.identityProvider,
            externalIdentityId = user.externalIdentityId,
            email = user.email,
            displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").takeIf { it.isNotBlank() }
                ?: user.email
                ?: user.username
        )
    }
}

