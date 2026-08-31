package com.easy.bpm.security

import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OidcIdentityService(
    private val appUserRepository: AppUserRepository,
    private val userGroupRepository: UserGroupRepository,
    private val permissionRepository: PermissionRepository,
    private val properties: ExternalAuthenticationProperties
) {
    @Transactional
    fun loadOrProvision(jwt: Jwt): AuthenticatedUser {
        val providerName = properties.identityProviderName()
        val subject = jwt.subject?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadCredentialsException("OIDC token missing required 'sub' claim")
        val username = resolveUsername(jwt)
        val email = jwt.claimAsString("email")
        val firstName = jwt.claimAsString("given_name")
        val lastName = jwt.claimAsString("family_name")
        val tokenGroups = extractGroups(jwt)
        val mappedPermissionCodes = extractMappedPermissionCodes(jwt)
        val defaultPermissionCodes = properties.userProvisioning.defaultPermissionCodes.toSet()

        val user = appUserRepository.findByIdentityProviderAndExternalIdentityId(providerName, subject)
            ?: appUserRepository.findByUsername(username)
            ?: provisionUser(providerName, subject, username, email, firstName, lastName, tokenGroups, defaultPermissionCodes)

        if (user.externalIdentityId == null && user.identityProvider == "LOCAL") {
            user.identityProvider = providerName
            user.externalIdentityId = subject
        }

        user.email = email ?: user.email
        user.firstName = firstName ?: user.firstName
        user.lastName = lastName ?: user.lastName
        user.updatedAt = LocalDateTime.now()
        if (properties.oidc.syncGroups) {
            user.groups = syncGroups(tokenGroups)
        }
        val saved = appUserRepository.save(user)

        val localGroupCodes = saved.groups.map { it.code }.toSet()
        val permissionsFromGroups = saved.groups.flatMap { group -> group.permissions.map { it.code } }.toSet()
        val directPermissions = saved.permissions.map { it.code }.toSet()
        val allGroups = localGroupCodes + tokenGroups
        val allPermissions = permissionsFromGroups + directPermissions + mappedPermissionCodes + defaultPermissionCodes

        return AuthenticatedUser(
            userId = saved.id,
            usernameValue = saved.username,
            passwordValue = saved.passwordHash,
            enabledValue = saved.enabled,
            groups = allGroups,
            permissionCodes = allPermissions,
            identityProvider = saved.identityProvider,
            externalIdentityId = saved.externalIdentityId,
            email = saved.email,
            displayName = listOfNotNull(saved.firstName, saved.lastName).joinToString(" ").takeIf { it.isNotBlank() }
                ?: saved.email
                ?: saved.username
        )
    }

    private fun provisionUser(
        providerName: String,
        subject: String,
        username: String,
        email: String?,
        firstName: String?,
        lastName: String?,
        groups: Set<String>,
        permissionCodes: Set<String>
    ): AppUser {
        if (!properties.userProvisioning.enabled) {
            throw UsernameNotFoundException("OIDC user '$username' is not provisioned in Easy BPM")
        }

        return appUserRepository.save(
            AppUser(
                username = username,
                passwordHash = "{OIDC}",
                enabled = true,
                identityProvider = providerName,
                externalIdentityId = subject,
                email = email,
                firstName = firstName,
                lastName = lastName,
                groups = syncGroups(groups),
                permissions = findPermissions(permissionCodes).toMutableSet(),
                createdBy = providerName.lowercase(),
                updatedBy = providerName.lowercase()
            )
        )
    }

    private fun resolveUsername(jwt: Jwt): String {
        val preferred = jwt.claimAsString(properties.oidc.usernameClaim)
            ?: jwt.claimAsString("preferred_username")
            ?: jwt.claimAsString("email")
            ?: jwt.subject
        return preferred?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadCredentialsException("OIDC token missing a usable username claim")
    }

    private fun extractGroups(jwt: Jwt): Set<String> {
        val rawGroups = jwt.claimAsStringList(properties.oidc.groupClaim)
        return rawGroups
            .map { it.trim().trim('/') }
            .filter { it.isNotBlank() }
            .map { properties.oidc.groupMappings[it] ?: it }
            .toSet()
    }

    private fun extractMappedPermissionCodes(jwt: Jwt): Set<String> =
        properties.oidc.roleClaimPaths
            .flatMap { extractStringListAtPath(jwt.claims, it.replace("\${client-id}", properties.oidc.clientId)) }
            .mapNotNull { properties.oidc.roleMappings[it] }
            .toSet()

    @Suppress("UNCHECKED_CAST")
    private fun extractStringListAtPath(claims: Map<String, Any>, path: String): List<String> {
        val value = path.split(".").fold(claims as Any?) { current, part ->
            when (current) {
                is Map<*, *> -> current[part]
                else -> null
            }
        }
        return when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
    }

    private fun syncGroups(groupCodes: Set<String>): MutableSet<UserGroup> =
        groupCodes.map { code ->
            userGroupRepository.findByCode(code)
                ?: userGroupRepository.save(UserGroup(code = code, name = code))
        }.toMutableSet()

    private fun findPermissions(permissionCodes: Set<String>): Set<Permission> =
        if (permissionCodes.isEmpty()) emptySet() else permissionRepository.findAllByCodeIn(permissionCodes).toSet()

    private fun Jwt.claimAsString(name: String): String? =
        claims[name]?.toString()?.takeIf { it.isNotBlank() }

    private fun Jwt.claimAsStringList(name: String): List<String> {
        val claim = claims[name] ?: return emptyList()
        return when (claim) {
            is Collection<*> -> claim.mapNotNull { it?.toString() }
            is String -> claim.split(",")
            else -> emptyList()
        }
    }
}
