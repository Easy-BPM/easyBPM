package com.easy.bpm.repository.document

import com.easy.bpm.model.document.Document
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByTaskId(taskId: Long): List<Document>
    fun findByProcessInstanceId(processInstanceId: Long): List<Document>
    fun countByProcessInstanceId(processInstanceId: Long): Long
    fun findByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String): List<Document>
    fun deleteByTaskIdAndFormFieldKey(taskId: Long, formFieldKey: String)

    @Modifying
    fun deleteByTaskId(taskId: Long): Int

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int

    @Query(
        """
        SELECT COUNT(DISTINCT d.id)
        FROM Document d
        WHERE d.processInstanceId = :processInstanceId
           OR d.taskId IN :taskIds
        """
    )
    fun countForProcessInstanceCleanup(processInstanceId: Long, taskIds: Collection<Long>): Long

    @Modifying
    @Query("delete from Document d where d.taskId in :taskIds")
    fun deleteByTaskIdIn(taskIds: Collection<Long>): Int

    @Query("select count(d) from Document d where d.taskId in :taskIds")
    fun countByTaskIdIn(taskIds: Collection<Long>): Long
}
