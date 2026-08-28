package com.easy.bpm.security

import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class OidcIdentityServiceTest : FunSpec({
    lateinit var appUserRepository: AppUserRepository
    lateinit var userGroupRepository: UserGroupRepository
    lateinit var permissionRepository: PermissionRepository
    lateinit var properties: ExternalAuthenticationProperties
    lateinit var service: OidcIdentityService

    beforeTest {
        appUserRepository = mockk()
        userGroupRepository = mockk()
        permissionRepository = mockk()
        properties = ExternalAuthenticationProperties().apply {
            provider = "keycloak"
            userProvisioning.enabled = true
            userProvisioning.defaultPermissionCodes = listOf(AppPermissions.ACCESS_PROCESS_PORTAL)
            oidc.clientId = "easybpm"
            oidc.roleClaimPaths = listOf("realm_access.roles", "resource_access.easybpm.roles")
            oidc.roleMappings = mapOf(
                "easybpm-admin" to AppPermissions.ACCESS_BPM_ADMIN,
                "easybpm-modeler" to AppPermissions.ACCESS_BPM_MODELER,
                "easybpm-user" to AppPermissions.ACCESS_PROCESS_PORTAL
            )
        }
        service = OidcIdentityService(appUserRepository, userGroupRepository, permissionRepository, properties)
    }

    test("provisions a Keycloak user from OIDC claims") {
        val portalPermission = Permission(code = AppPermissions.ACCESS_PROCESS_PORTAL, name = "Portal")
        val adminPermission = Permission(code = AppPermissions.ACCESS_BPM_ADMIN, name = "Admin")
        val supportGroup = UserGroup(code = "customer-support", name = "customer-support")

        every { appUserRepository.findByIdentityProviderAndExternalIdentityId("KEYCLOAK", "sub-123") } returns null
        every { appUserRepository.findByUsername("john.doe") } returns null
        every { permissionRepository.findAllByCodeIn(any<Collection<String>>()) } returns listOf(portalPermission, adminPermission)
        every { userGroupRepository.findByCode("customer-support") } returns supportGroup
        every { appUserRepository.save(any<AppUser>()) } answers {
            val user = firstArg<AppUser>()
            if (user.id == 0L) user.copy(id = 42L) else user
        }

        val principal = service.loadOrProvision(
            jwt(
                subject = "sub-123",
                claims = mapOf(
                    "preferred_username" to "john.doe",
                    "email" to "john@example.com",
                    "given_name" to "John",
                    "family_name" to "Doe",
                    "groups" to listOf("customer-support"),
                    "realm_access" to mapOf("roles" to listOf("easybpm-user", "easybpm-admin"))
                )
            )
        )

        principal.userId shouldBe 42L
        principal.username shouldBe "john.doe"
        principal.identityProvider shouldBe "KEYCLOAK"
        principal.externalIdentityId shouldBe "sub-123"
        principal.email shouldBe "john@example.com"
        principal.displayName shouldBe "John Doe"
        principal.groups shouldContain "customer-support"
        principal.permissionCodes shouldContain AppPermissions.ACCESS_PROCESS_PORTAL
        principal.permissionCodes shouldContain AppPermissions.ACCESS_BPM_ADMIN
    }

    test("loads an existing external identity without creating a new user") {
        val existing = AppUser(
            id = 7,
            username = "jane",
            passwordHash = "{OIDC}",
            identityProvider = "KEYCLOAK",
            externalIdentityId = "sub-456",
            groups = mutableSetOf(UserGroup(code = "finance", name = "Finance")),
            permissions = mutableSetOf(Permission(code = AppPermissions.ACCESS_PROCESS_PORTAL, name = "Portal"))
        )

        every { appUserRepository.findByIdentityProviderAndExternalIdentityId("KEYCLOAK", "sub-456") } returns existing
        every { userGroupRepository.findByCode("finance") } returns existing.groups.first()
        every { permissionRepository.findAllByCodeIn(any<Collection<String>>()) } returns emptyList()
        every { appUserRepository.save(any<AppUser>()) } answers { firstArg() }

        val principal = service.loadOrProvision(
            jwt(
                subject = "sub-456",
                claims = mapOf(
                    "preferred_username" to "jane",
                    "groups" to listOf("finance")
                )
            )
        )

        principal.userId shouldBe 7L
        principal.groups shouldContain "finance"
    }

    test("rejects tokens without sub claim") {
        shouldThrow<BadCredentialsException> {
            service.loadOrProvision(jwt(subject = null, claims = mapOf("preferred_username" to "missing-sub")))
        }
    }
})

private fun jwt(subject: String?, claims: Map<String, Any>): Jwt {
    val now = Instant.now()
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject(subject)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claims { it.putAll(claims) }
        .build()
}
