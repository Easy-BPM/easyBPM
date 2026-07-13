package com.easy.bpm.repository.security

import com.easy.bpm.model.security.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findAllByTenantId(tenantId: String): List<AppUser>
    fun findByTenantIdAndUsername(tenantId: String, username: String): AppUser?
    fun existsByTenantIdAndUsername(tenantId: String, username: String): Boolean
    fun findAllByTenantIdAndGroups_Id(tenantId: String, groupId: Long): List<AppUser>

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByUsername(username: String): AppUser? = findByTenantIdAndUsername("default", username)

    @Deprecated("Use tenant-scoped lookup instead")
    fun existsByUsername(username: String): Boolean = existsByTenantIdAndUsername("default", username)

    @Deprecated("Use tenant-scoped lookup instead")
    fun findAllByGroups_Id(groupId: Long): List<AppUser> = findAllByTenantIdAndGroups_Id("default", groupId)
}
