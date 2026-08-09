package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.HistoricTaskVariable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface HistoricTaskVariableRepository : JpaRepository<HistoricTaskVariable, Long> {
    fun findByTaskId(taskId: Long): List<HistoricTaskVariable>
    fun findByProcessInstanceId(processInstanceId: Long): List<HistoricTaskVariable>
    fun countByTaskId(taskId: Long): Long
    fun countByProcessInstanceId(processInstanceId: Long): Long

    fun countByTaskIdIn(taskIds: Collection<Long>): Long

    @Modifying
    fun deleteByTaskIdIn(taskIds: Collection<Long>): Int

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int
}
