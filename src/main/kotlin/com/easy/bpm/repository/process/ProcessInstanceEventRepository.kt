package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstanceEvent
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessInstanceEventRepository : JpaRepository<ProcessInstanceEvent, Long> {
    fun findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstanceId: Long): List<ProcessInstanceEvent>
    fun deleteByProcessInstanceId(processInstanceId: Long)
}
