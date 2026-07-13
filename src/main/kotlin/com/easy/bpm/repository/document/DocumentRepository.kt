package com.easy.bpm.repository.document

import com.easy.bpm.model.document.Document
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByTenantIdAndTaskId(tenantId: String, taskId: Long): List<Document>
    fun findByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long): List<Document>
    fun findByTenantIdAndTaskIdAndFormFieldKey(tenantId: String, taskId: Long, formFieldKey: String): List<Document>
    fun deleteByTenantIdAndTaskIdAndFormFieldKey(tenantId: String, taskId: Long, formFieldKey: String)
    fun deleteByTenantIdAndTaskId(tenantId: String, taskId: Long)
    fun deleteByTenantIdAndProcessInstanceId(tenantId: String, processInstanceId: Long)

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByTaskId(taskId: Long): List<Document> = findByTenantIdAndTaskId("default", taskId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessInstanceId(processInstanceId: Long): List<Document> = findByTenantIdAndProcessInstanceId("default", processInstanceId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun findByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String): List<Document> = findByTenantIdAndTaskIdAndFormFieldKey("default", taskId, formFieldKey)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String) = deleteByTenantIdAndTaskIdAndFormFieldKey("default", taskId, formFieldKey)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByTaskId(taskId: Long) = deleteByTenantIdAndTaskId("default", taskId)
    @Deprecated("Use tenant-scoped lookup instead")
    fun deleteByProcessInstanceId(processInstanceId: Long) = deleteByTenantIdAndProcessInstanceId("default", processInstanceId)
}
