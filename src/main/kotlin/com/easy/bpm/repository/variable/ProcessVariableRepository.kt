package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.ProcessVariable
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessVariableRepository : JpaRepository<ProcessVariable, Long> {
    fun findByProcessInstanceId(processInstanceId: Long): List<ProcessVariable>
    fun findByProcessInstanceIdAndName(processInstanceId: Long, name: String): ProcessVariable?
    fun deleteByProcessInstanceId(processInstanceId: Long)
}
