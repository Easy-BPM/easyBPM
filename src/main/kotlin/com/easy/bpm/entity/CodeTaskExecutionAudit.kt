package com.easy.bpm.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * CodeTaskExecutionAudit - Records execution of Code Tasks for audit trail
 *
 * Every time a Code Task executes (succeeds or fails), a record is created.
 * Stores input/output variables (JSONB) and execution metrics.
 */
@Entity
@Table(
  name = "code_task_execution",
  indexes = [
    Index(name = "idx_code_execution_instance_id", columnList = "instance_id"),
    Index(name = "idx_code_execution_jar_id", columnList = "jar_id"),
    Index(name = "idx_code_execution_status", columnList = "status"),
    Index(name = "idx_code_execution_executed_at", columnList = "executed_at DESC")
  ]
)
class CodeTaskExecutionAudit(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "instance_id", nullable = false)
  val instanceId: Long,

  @Column(name = "node_id", length = 255)
  val nodeId: String? = null,

  @Column(name = "jar_id")
  val jarId: Long? = null,

  @Column(name = "class_name", length = 500)
  val className: String? = null,

  @Column(name = "method_name", length = 255)
  val methodName: String? = null,

  @Column(name = "input_variables")
  val inputVariables: String? = null, // JSON object of input variable snapshots

  @Column(name = "output_variables")
  val outputVariables: String? = null, // JSON object of output variable snapshots

  @Column(name = "execution_time_ms")
  val executionTimeMs: Int = 0,

  @Column(name = "status", length = 50)
  val status: String = "PENDING", // COMPLETED, FAILED, TIMEOUT

  @Column(name = "error_message", columnDefinition = "TEXT")
  val errorMessage: String? = null,

  @Column(name = "executed_at", nullable = false)
  val executedAt: LocalDateTime = LocalDateTime.now()
) {
  override fun toString(): String =
    "CodeTaskExecutionAudit(id=$id, instanceId=$instanceId, className='$className', methodName='$methodName', status='$status', executionTimeMs=$executionTimeMs)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CodeTaskExecutionAudit) return false
    if (id != other.id) return false
    return true
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  companion object {
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_FAILED = "FAILED"
    const val STATUS_TIMEOUT = "TIMEOUT"

    /**
     * Example usage:
     * CodeTaskExecutionAudit(
     *   instanceId = 123,
     *   nodeId = "codeTask_1",
     *   jarId = 1,
     *   className = "com.acme.OrderCalculator",
     *   methodName = "calculateTotal",
     *   inputVariables = """{"order":{"id":456,"amount":100},"taxRate":0.08}""",
     *   outputVariables = """{"orderTotal":{"subtotal":100,"tax":8}}""",
     *   executionTimeMs = 145,
     *   status = STATUS_COMPLETED
     * )
     */
  }
}

