package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstanceEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface ProcessInstanceEventRepository : JpaRepository<ProcessInstanceEvent, Long> {
    fun findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstanceId: Long): List<ProcessInstanceEvent>
    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int
}
