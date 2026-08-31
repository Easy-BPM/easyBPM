package com.easy.bpm.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "easybpm.authentication")
class ExternalAuthenticationProperties {
    var provider: String = "local"
    var userProvisioning: UserProvisioning = UserProvisioning()
    var oidc: Oidc = Oidc()

    fun isOidcEnabled(): Boolean =
        provider.equals("oidc", ignoreCase = true) || provider.equals("keycloak", ignoreCase = true)

    fun identityProviderName(): String =
        if (provider.equals("keycloak", ignoreCase = true)) "KEYCLOAK" else provider.uppercase()

    class UserProvisioning {
        var enabled: Boolean = true
        var defaultPermissionCodes: List<String> = listOf(AppPermissions.ACCESS_PROCESS_PORTAL)
    }

    class Oidc {
        var issuerUri: String = ""
        var jwkSetUri: String = ""
        var clientId: String = "easybpm"
        var audience: String = ""
        var groupClaim: String = "groups"
        var usernameClaim: String = "preferred_username"
        var roleClaimPaths: List<String> = listOf("realm_access.roles", "resource_access.easybpm.roles")
        var roleMappings: Map<String, String> = mapOf(
            "easybpm-admin" to AppPermissions.ACCESS_BPM_ADMIN,
            "easybpm-modeler" to AppPermissions.ACCESS_BPM_MODELER,
            "easybpm-user" to AppPermissions.ACCESS_PROCESS_PORTAL,
            "easybpm-admin-users-read" to AppPermissions.VIEW_USERS,
            "easybpm-admin-users-manage" to AppPermissions.MANAGE_USERS,
            "easybpm-admin-groups-read" to AppPermissions.VIEW_GROUPS,
            "easybpm-admin-groups-manage" to AppPermissions.MANAGE_GROUPS,
            "easybpm-admin-permissions-manage" to AppPermissions.MANAGE_PERMISSIONS,
            "easybpm-admin-secrets-read" to AppPermissions.VIEW_SECRETS,
            "easybpm-admin-secrets-manage" to AppPermissions.MANAGE_SECRETS
        )
        var groupMappings: Map<String, String> = emptyMap()
        var syncGroups: Boolean = true
    }
}
