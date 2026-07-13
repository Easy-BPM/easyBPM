package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstanceEvent
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessInstanceEventRepository : JpaRepository<ProcessInstanceEvent, Long> {
    fun findByTenantIdAndProcessInstanceIdOrderByCreatedAtAscIdAsc(tenantId: String, processInstanceId: Long): List<ProcessInstanceEvent>
    fun deleteByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long)

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstanceId: Long): List<ProcessInstanceEvent> =
        findByTenantIdAndProcessInstanceIdOrderByCreatedAtAscIdAsc("default", processInstanceId)

    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByProcessInstanceId(processInstanceId: Long) = deleteByTenantIdAndProcessInstanceId("default", processInstanceId)
}
