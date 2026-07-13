package com.easy.bpm.repository.security

import com.easy.bpm.model.security.Tenant
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findByCode(code: String): Tenant?
    fun existsByCode(code: String): Boolean
}
