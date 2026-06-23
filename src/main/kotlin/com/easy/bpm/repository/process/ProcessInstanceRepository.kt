package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.enum.ProcessStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ProcessInstanceRepository : JpaRepository<ProcessInstance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ProcessInstance?

    /**
     * Find all child process instances for a given parent instance.
     * Used for hierarchy visualization in Admin UI and parent-child navigation.
     */
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.parentInstanceId = ?1 ORDER BY pi.id DESC")
    fun findByParentInstanceId(parentInstanceId: Long): List<ProcessInstance>

    /**
     * Find a parent process instance for a given child instance.
     * Used to navigate up the hierarchy (child → parent).
     */
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.id = ?1")
    fun findParentOfChild(childInstanceId: Long): ProcessInstance?

    /**
     * Check if an instance is a subprocess (has a parent).
     */
    @Query("SELECT COUNT(pi) > 0 FROM ProcessInstance pi WHERE pi.id = ?1 AND pi.parentInstanceId IS NOT NULL")
    fun isSubprocess(instanceId: Long): Boolean

    /**
     * Find all process instances at a specific nesting level.
     * Used for filtering and analytics.
     */
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.nestingLevel = ?1 ORDER BY pi.id DESC")
    fun findByNestingLevel(level: Int): List<ProcessInstance>

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

    fun findByProcessDefinitionId(processDefinitionId: Long): List<ProcessInstance>
}
