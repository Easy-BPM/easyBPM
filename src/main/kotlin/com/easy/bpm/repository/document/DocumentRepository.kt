package com.easy.bpm.repository.document

import com.easy.bpm.model.document.Document
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByTaskId(taskId: Long): List<Document>
    fun findByProcessInstanceId(processInstanceId: Long): List<Document>
    fun findByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String): List<Document>
    fun deleteByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String)
}
