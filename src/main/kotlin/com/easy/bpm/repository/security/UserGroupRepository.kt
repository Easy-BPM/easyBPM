package com.easy.bpm.repository.security

import com.easy.bpm.model.security.UserGroup
import org.springframework.data.jpa.repository.JpaRepository

interface UserGroupRepository : JpaRepository<UserGroup, Long> {
    fun findAllByTenantId(tenantId: String): List<UserGroup>
    fun findByTenantIdAndCode(tenantId: String, code: String): UserGroup?

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByCode(code: String): UserGroup? = findByTenantIdAndCode("default", code)
}
