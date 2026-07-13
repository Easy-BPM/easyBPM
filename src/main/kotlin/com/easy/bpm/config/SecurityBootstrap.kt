package com.easy.bpm.config

import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.model.security.Tenant
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import com.easy.bpm.repository.security.TenantRepository
import com.easy.bpm.security.AppPermissions
import jakarta.transaction.Transactional
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import com.easy.bpm.tenant.TenantContext

@Component
class SecurityBootstrap(
    private val permissionRepository: PermissionRepository,
    private val userGroupRepository: UserGroupRepository,
    private val tenantRepository: TenantRepository,
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${easybpm.security.bootstrap.admin-group-code:ADMIN}") private val adminGroupCode: String,
    @Value("\${easybpm.security.bootstrap.admin-group-name:Administrators}") private val adminGroupName: String,
    @Value("\${easybpm.security.bootstrap.admin-username:admin}") private val adminUsername: String,
    @Value("\${easybpm.security.bootstrap.admin-password:admin}") private val adminPassword: String
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val defaultTenant = tenantRepository.findByCode(TenantContext.DEFAULT_TENANT) ?: tenantRepository.save(
            Tenant(code = TenantContext.DEFAULT_TENANT, name = "Default Tenant")
        )

        val permissions = AppPermissions.all.map { code ->
            permissionRepository.findByCode(code)
                ?: permissionRepository.save(Permission(code = code, name = code.replace("_", " ")))
        }.toSet()

        val adminGroup = userGroupRepository.findByTenantIdAndCode(defaultTenant.code, adminGroupCode) ?: userGroupRepository.save(
            UserGroup(
                code = adminGroupCode,
                tenantId = defaultTenant.code,
                name = adminGroupName,
                permissions = permissions.toMutableSet()
            )
        )

        if (appUserRepository.findByTenantIdAndUsername(defaultTenant.code, adminUsername) == null) {
            appUserRepository.save(
                AppUser(
                    username = adminUsername,
                    tenantId = defaultTenant.code,
                    passwordHash = passwordEncoder.encode(adminPassword),
                    enabled = true,
                    groups = mutableSetOf(adminGroup),
                    permissions = mutableSetOf(),
                    createdBy = "bootstrap",
                    updatedBy = "bootstrap"
                )
            )
        }
    }
}

