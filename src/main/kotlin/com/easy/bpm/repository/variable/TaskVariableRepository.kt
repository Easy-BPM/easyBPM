package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.TaskVariable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface TaskVariableRepository : JpaRepository<TaskVariable, Long> {
    fun findByTaskId(taskId: Long): List<TaskVariable>
    fun findByProcessInstanceId(processInstanceId: Long): List<TaskVariable>
    fun countByTaskId(taskId: Long): Long
    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Modifying
    fun deleteByTaskId(taskId: Long): Int

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int

    fun findByTaskIdAndName(taskId: Long, name: String): TaskVariable?
    fun findAllByTaskIdAndNameOrderByIdDesc(taskId: Long, name: String): List<TaskVariable>

    @Query("select count(tv) from TaskVariable tv where tv.taskId in :taskIds")
    fun countByTaskIdIn(taskIds: Collection<Long>): Long

    @Modifying
    @Query("delete from TaskVariable tv where tv.taskId in :taskIds")
    fun deleteByTaskIdIn(taskIds: Collection<Long>): Int
}
