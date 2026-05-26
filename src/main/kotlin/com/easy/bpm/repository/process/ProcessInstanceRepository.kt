package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.enum.ProcessStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ProcessInstanceRepository : JpaRepository<ProcessInstance, Long> {

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

    /**
     * Count instances by status for metrics dashboard
     */
    fun countByStatus(status: ProcessStatus): Long

    /**
     * Count instances created between dates for metrics dashboard
     */
    @Query("SELECT COUNT(pi) FROM ProcessInstance pi WHERE pi.createdAt BETWEEN ?1 AND ?2")
    fun countByCreatedAtBetween(from: LocalDateTime, to: LocalDateTime): Long

    /**
     * Find all instances for a specific process definition for metrics
     */
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.processDefinition.id = ?1")
    fun findByProcessDefinitionId(processDefinitionId: Long): List<ProcessInstance>

    /**
     * Count instances by status within a date range for metrics
     */
    @Query("SELECT COUNT(pi) FROM ProcessInstance pi WHERE pi.status = ?1 AND pi.createdAt BETWEEN ?2 AND ?3")
    fun countByStatusAndCreatedAtBetween(status: ProcessStatus, from: LocalDateTime, to: LocalDateTime): Long

    /**
     * Find instances with specific statuses for incidents view
     */
    fun findByStatusIn(statuses: List<ProcessStatus>): List<ProcessInstance>
}
