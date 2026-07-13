package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.ProcessVariable
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessVariableRepository : JpaRepository<ProcessVariable, Long> {
    fun findByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long): List<ProcessVariable>
    fun findByTenantIdAndProcessInstanceIdAndName(tenantId: String, processInstanceId: Long, name: String): ProcessVariable?
    fun deleteByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long)

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceId(processInstanceId: Long): List<ProcessVariable> = findByTenantIdAndProcessInstanceId("default", processInstanceId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceIdAndName(processInstanceId: Long, name: String): ProcessVariable? = findByTenantIdAndProcessInstanceIdAndName("default", processInstanceId, name)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByProcessInstanceId(processInstanceId: Long) = deleteByTenantIdAndProcessInstanceId("default", processInstanceId)
}
