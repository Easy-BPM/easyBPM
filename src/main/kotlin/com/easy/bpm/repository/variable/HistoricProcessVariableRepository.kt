package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.HistoricProcessVariable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface HistoricProcessVariableRepository : JpaRepository<HistoricProcessVariable, Long> {
    fun findByProcessInstanceId(processInstanceId: Long): List<HistoricProcessVariable>
    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int
}
