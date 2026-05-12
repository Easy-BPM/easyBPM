package com.easy.bpm.config

import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import com.easy.bpm.security.AppPermissions
import jakarta.transaction.Transactional
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SecurityBootstrap(
    private val permissionRepository: PermissionRepository,
    private val userGroupRepository: UserGroupRepository,
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${easybpm.security.bootstrap.admin-group-code:ADMIN}") private val adminGroupCode: String,
    @Value("\${easybpm.security.bootstrap.admin-group-name:Administrators}") private val adminGroupName: String,
    @Value("\${easybpm.security.bootstrap.admin-username:admin}") private val adminUsername: String,
    @Value("\${easybpm.security.bootstrap.admin-password:admin}") private val adminPassword: String
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val permissions = AppPermissions.all.map { code ->
            permissionRepository.findByCode(code)
                ?: permissionRepository.save(Permission(code = code, name = code.replace("_", " ")))
        }.toSet()

        val adminGroup = userGroupRepository.findByCode(adminGroupCode) ?: userGroupRepository.save(
            UserGroup(
                code = adminGroupCode,
                name = adminGroupName,
                permissions = permissions.toMutableSet()
            )
        )

        if (appUserRepository.findByUsername(adminUsername) == null) {
            appUserRepository.save(
                AppUser(
                    username = adminUsername,
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

