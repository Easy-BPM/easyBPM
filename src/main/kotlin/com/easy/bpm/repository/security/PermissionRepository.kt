package com.easy.bpm.repository.security

import com.easy.bpm.model.security.Permission
import org.springframework.data.jpa.repository.JpaRepository

interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByCode(code: String): Permission?
    fun findAllByCodeIn(codes: Collection<String>): List<Permission>
}

