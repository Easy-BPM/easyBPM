package com.easy.bpm.repository.agent

import com.easy.bpm.model.agent.AgentProcessDefinition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AgentProcessDefinitionRepository : JpaRepository<AgentProcessDefinition, Long> {
    fun findTopByKeyOrderByVersionDesc(key: String): AgentProcessDefinition?

    @Query(
        """
        SELECT d FROM AgentProcessDefinition d
        WHERE d.version = (
            SELECT MAX(d2.version)
            FROM AgentProcessDefinition d2
            WHERE d2.key = d.key
        )
        ORDER BY d.key ASC
        """
    )
    fun findLatestDefinitions(): List<AgentProcessDefinition>

    @Query("SELECT d FROM AgentProcessDefinition d WHERE d.key = :key ORDER BY d.version DESC")
    fun findVersionsByKey(@Param("key") key: String): List<AgentProcessDefinition>
}
