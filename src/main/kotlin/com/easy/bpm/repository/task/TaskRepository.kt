package com.easy.bpm.repository.task

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import java.util.Optional

interface TaskRepository : JpaRepository<Task, Long> {
    fun findByTenantId(tenantId: String, pageable: Pageable): Page<Task>
    fun findByTenantIdAndAssignee(tenantId: String, assignee: String, pageable: Pageable): Page<Task>
    fun findByTenantIdAndStatus(tenantId: String, status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByTenantIdAndAssigneeAndStatus(tenantId: String, assignee: String, status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long): List<Task>
    fun findByTenantIdAndProcessInstanceIdAndNodeIdAndStatus(tenantId: String, processInstanceId: Long, nodeId: String, status: TaskStatus): List<Task>
    fun deleteByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.tenantId = :tenantId and t.id = :id")
    fun findByTenantIdAndIdForUpdate(tenantId: String, id: Long): Optional<Task>

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByAssignee(assignee: String, pageable: Pageable): Page<Task> = findByTenantIdAndAssignee("default", assignee, pageable)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByStatus(status: TaskStatus, pageable: Pageable): Page<Task> = findByTenantIdAndStatus("default", status, pageable)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByAssigneeAndStatus(assignee: String, status: TaskStatus, pageable: Pageable): Page<Task> = findByTenantIdAndAssigneeAndStatus("default", assignee, status, pageable)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceId(processInstanceId: Long): List<Task> = findByTenantIdAndProcessInstanceId("default", processInstanceId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceIdAndNodeIdAndStatus(processInstanceId: Long, nodeId: String, status: TaskStatus): List<Task> = findByTenantIdAndProcessInstanceIdAndNodeIdAndStatus("default", processInstanceId, nodeId, status)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByProcessInstanceId(processInstanceId: Long) = deleteByTenantIdAndProcessInstanceId("default", processInstanceId)

    @Deprecated("Use tenant-scoped lookup instead")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    fun findByIdForUpdate(id: Long): Optional<Task>
}
