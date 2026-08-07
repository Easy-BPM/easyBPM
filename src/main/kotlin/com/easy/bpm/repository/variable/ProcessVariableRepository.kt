package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.ProcessVariable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface ProcessVariableRepository : JpaRepository<ProcessVariable, Long> {
    fun findByProcessInstanceId(processInstanceId: Long): List<ProcessVariable>
    fun findByProcessInstanceIdAndName(processInstanceId: Long, name: String): ProcessVariable?
    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int
}
