package com.easy.bpm.repository.variable

import com.easy.bpm.model.variable.TaskVariable
import org.springframework.data.jpa.repository.JpaRepository

interface TaskVariableRepository : JpaRepository<TaskVariable, Long> {
    fun findByTenantIdAndTaskId(tenantId: String, taskId: Long): List<TaskVariable>
    fun deleteByTenantIdAndTaskId(tenantId: String, taskId: Long)
    fun findByTenantIdAndTaskIdAndName(tenantId: String, taskId: Long, name: String): TaskVariable?
    fun findAllByTenantIdAndTaskIdAndNameOrderByIdDesc(tenantId: String, taskId: Long, name: String): List<TaskVariable>

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByTaskId(taskId: Long): List<TaskVariable> = findByTenantIdAndTaskId("default", taskId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByTaskId(taskId: Long) = deleteByTenantIdAndTaskId("default", taskId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByTaskIdAndName(taskId: Long, name: String): TaskVariable? = findByTenantIdAndTaskIdAndName("default", taskId, name)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findAllByTaskIdAndNameOrderByIdDesc(taskId: Long, name: String): List<TaskVariable> = findAllByTenantIdAndTaskIdAndNameOrderByIdDesc("default", taskId, name)
}
