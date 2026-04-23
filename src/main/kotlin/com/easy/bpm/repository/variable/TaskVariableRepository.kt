package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.TaskVariable
import org.springframework.data.jpa.repository.JpaRepository

interface TaskVariableRepository : JpaRepository<TaskVariable, Long> {
    fun findByTaskId(taskId: Long): List<TaskVariable>
    fun deleteByTaskId(taskId: Long)
    fun findByTaskIdAndName(taskId: Long, name: String): TaskVariable?
}

