package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProcessDefinitionRepository : JpaRepository<ProcessDefinition, Long> {
    fun findTopByNameOrderByVersionDesc(name: String): ProcessDefinition?

    @Query("""
        SELECT pd FROM ProcessDefinition pd
        WHERE pd.version = (
            SELECT MAX(pd2.version) 
            FROM ProcessDefinition pd2 
            WHERE pd2.name = pd.name
        )
    """)
    fun findLatestVersionProcesses(pageable: Pageable): Page<ProcessDefinition>
}
