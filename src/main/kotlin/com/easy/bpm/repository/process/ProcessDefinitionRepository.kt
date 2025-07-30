package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessDefinitionRepository : JpaRepository<ProcessDefinition, Long> {
    fun findTopByNameOrderByVersionDesc(name: String): ProcessDefinition?
}
