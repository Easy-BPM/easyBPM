package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessDefinition
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProcessDefinitionRepository : JpaRepository<ProcessDefinition, Long> {
    fun findTopByTenantIdAndKeyOrderByVersionDesc(tenantId: String, key: String): ProcessDefinition?

    @Query("""
        SELECT pd FROM ProcessDefinition pd
        WHERE pd.tenantId = :tenantId
          AND pd.version = (
            SELECT MAX(pd2.version)
            FROM ProcessDefinition pd2
            WHERE pd2.tenantId = :tenantId AND pd2.key = pd.key
        )
    """)
    fun findLatestVersionProcesses(@Param("tenantId") tenantId: String, pageable: Pageable): Page<ProcessDefinition>

    @Query("""
        SELECT pd FROM ProcessDefinition pd
        WHERE pd.tenantId = :tenantId
          AND pd.version = (
            SELECT MAX(pd2.version)
            FROM ProcessDefinition pd2
            WHERE pd2.tenantId = :tenantId AND pd2.key = pd.key
        )
        ORDER BY pd.id ASC
    """)
    fun findLatestVersionProcessDefinitions(@Param("tenantId") tenantId: String): List<ProcessDefinition>

    @Deprecated("Use tenant-scoped lookup instead")
    fun findTopByKeyOrderByVersionDesc(key: String): ProcessDefinition? = findTopByTenantIdAndKeyOrderByVersionDesc("default", key)
}
