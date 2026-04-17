package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessDefinition
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProcessDefinitionRepository : JpaRepository<ProcessDefinition, Long> {
    fun findTopByKeyOrderByVersionDesc(key: String): ProcessDefinition?

    @Query("""
        SELECT pd FROM ProcessDefinition pd
        WHERE pd.version = (
            SELECT MAX(pd2.version) 
            FROM ProcessDefinition pd2 
            WHERE pd2.key = pd.key
        )
    """)
    fun findLatestVersionProcesses(pageable: Pageable): Page<ProcessDefinition>
}
