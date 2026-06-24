package com.easy.bpm.repository.agent

import com.easy.bpm.model.agent.AgentProcessExecution
import org.springframework.data.jpa.repository.JpaRepository

interface AgentProcessExecutionRepository : JpaRepository<AgentProcessExecution, Long> {
    fun findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstanceId: Long): List<AgentProcessExecution>
}
