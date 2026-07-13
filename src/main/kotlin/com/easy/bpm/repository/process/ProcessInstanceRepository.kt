package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.enum.ProcessStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ProcessInstanceRepository : JpaRepository<ProcessInstance, Long> {
    fun findByTenantId(tenantId: String, pageable: Pageable): Page<ProcessInstance>
    fun findByTenantIdAndId(tenantId: String, id: Long): ProcessInstance?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.tenantId = :tenantId AND pi.id = :id")
    fun findByTenantIdAndIdForUpdate(@Param("tenantId") tenantId: String, @Param("id") id: Long): ProcessInstance?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ProcessInstance?

    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.tenantId = :tenantId AND pi.parentInstanceId = :parentInstanceId ORDER BY pi.id DESC")
    fun findByTenantIdAndParentInstanceId(@Param("tenantId") tenantId: String, @Param("parentInstanceId") parentInstanceId: Long): List<ProcessInstance>

    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.parentInstanceId = ?1 ORDER BY pi.id DESC")
    fun findByParentInstanceId(parentInstanceId: Long): List<ProcessInstance>

    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.id = ?1")
    fun findParentOfChild(childInstanceId: Long): ProcessInstance?

    @Query("SELECT COUNT(pi) > 0 FROM ProcessInstance pi WHERE pi.id = ?1 AND pi.parentInstanceId IS NOT NULL")
    fun isSubprocess(instanceId: Long): Boolean

    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.nestingLevel = ?1 ORDER BY pi.id DESC")
    fun findByNestingLevel(level: Int): List<ProcessInstance>

    @Query(
        """
        SELECT pi FROM ProcessInstance pi
        WHERE pi.tenantId = :tenantId
          AND pi.status = :status
          AND pi.updatedAt < :before
          AND (:processDefinitionId IS NULL OR pi.processDefinition.id = :processDefinitionId)
          AND (:processKey IS NULL OR pi.processDefinition.key = :processKey)
        ORDER BY pi.updatedAt ASC, pi.id ASC
        """
    )
    fun findTenantPurgeCandidates(
        @Param("tenantId") tenantId: String,
        @Param("status") status: ProcessStatus,
        @Param("before") before: LocalDateTime,
        @Param("processDefinitionId") processDefinitionId: Long?,
        @Param("processKey") processKey: String?
    ): List<ProcessInstance>


    @Query(
        """
        SELECT pi FROM ProcessInstance pi
        WHERE pi.status = :status
          AND pi.updatedAt < :before
          AND (:processDefinitionId IS NULL OR pi.processDefinition.id = :processDefinitionId)
          AND (:processKey IS NULL OR pi.processDefinition.key = :processKey)
        ORDER BY pi.updatedAt ASC, pi.id ASC
        """
    )
    fun findPurgeCandidates(
        @Param("status") status: ProcessStatus,
        @Param("before") before: LocalDateTime,
        @Param("processDefinitionId") processDefinitionId: Long?,
        @Param("processKey") processKey: String?
    ): List<ProcessInstance>

    fun findByTenantIdAndProcessDefinitionId(tenantId: String, processDefinitionId: Long): List<ProcessInstance>

    @Deprecated("Use tenant-scoped lookup instead")
    fun findByProcessDefinitionId(processDefinitionId: Long): List<ProcessInstance> = findByTenantIdAndProcessDefinitionId("default", processDefinitionId)
}
