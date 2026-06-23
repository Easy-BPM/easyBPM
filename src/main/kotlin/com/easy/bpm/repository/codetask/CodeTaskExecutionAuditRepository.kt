package com.easy.bpm.repository

import com.easy.bpm.entity.CodeTaskExecutionAudit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for CodeTaskExecutionAudit - Execution history and audit trail
 */
@Repository
interface CodeTaskExecutionAuditRepository : JpaRepository<CodeTaskExecutionAudit, Long> {

  /**
   * Find all executions for a process instance
   */
  fun findByInstanceId(instanceId: Long): List<CodeTaskExecutionAudit>

  /**
   * Find all executions for a process instance, ordered by execution time (most recent first)
   */
  fun findByInstanceIdOrderByExecutedAtDesc(instanceId: Long): List<CodeTaskExecutionAudit>

  /**
   * Find all executions of a specific JAR
   */
  fun findByJarId(jarId: Long): List<CodeTaskExecutionAudit>

  /**
   * Find all failed executions
   */
  fun findByStatus(status: String): List<CodeTaskExecutionAudit>

  /**
   * Find all failed executions for a specific instance
   */
  fun findByInstanceIdAndStatus(instanceId: Long, status: String): List<CodeTaskExecutionAudit>

  /**
   * Find executions paginated (for admin UI listing)
   */
  fun findByInstanceIdOrderByExecutedAtDesc(
    instanceId: Long,
    pageable: Pageable
  ): Page<CodeTaskExecutionAudit>

  /**
   * Find executions by status paginated
   */
  fun findByStatusOrderByExecutedAtDesc(
    status: String,
    pageable: Pageable
  ): Page<CodeTaskExecutionAudit>

  /**
   * Find executions by instance ID paginated
   */
  fun findByInstanceId(instanceId: Long, pageable: Pageable): Page<CodeTaskExecutionAudit>

  /**
   * Find executions by status paginated (without order)
   */
  fun findByStatus(status: String, pageable: Pageable): Page<CodeTaskExecutionAudit>

  /**
   * Find executions by instance ID and status paginated
   */
  fun findByInstanceIdAndStatus(
    instanceId: Long,
    status: String,
    pageable: Pageable
  ): Page<CodeTaskExecutionAudit>

  fun deleteByInstanceId(instanceId: Long)
}

