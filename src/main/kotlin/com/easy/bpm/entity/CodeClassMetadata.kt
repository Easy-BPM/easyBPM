package com.easy.bpm.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * CodeClassMetadata - Represents a discovered class and method in an uploaded JAR
 *
 * Stores metadata about classes and methods that can be invoked from processes.
 * Extracted via reflection when JAR is uploaded.
 */
@Entity
@Table(
  name = "code_class_metadata",
  uniqueConstraints = [
    UniqueConstraint(name = "uk_code_class_metadata", columnNames = ["jar_id", "class_name", "method_name"])
  ],
  indexes = [
    Index(name = "idx_code_class_jar_id", columnList = "jar_id"),
    Index(name = "idx_code_class_name", columnList = "class_name"),
    Index(name = "idx_code_class_method_name", columnList = "method_name")
  ]
)
class CodeClassMetadata(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "jar_id", nullable = false)
  val jarId: Long,

  @Column(name = "class_name", nullable = false, length = 500)
  val className: String,

  @Column(name = "method_name", nullable = false, length = 255)
  val methodName: String,

  @Column(name = "method_signature", columnDefinition = "TEXT")
  val methodSignature: String? = null,

  @Column(name = "input_params")
  @Lob
  val inputParams: String? = null, // JSON array of parameter metadata

  @Column(name = "return_type", length = 255)
  val returnType: String? = null,

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
) {
  override fun toString(): String =
    "CodeClassMetadata(id=$id, jarId=$jarId, className='$className', methodName='$methodName', signature='$methodSignature')"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CodeClassMetadata) return false
    if (id != other.id) return false
    if (jarId != other.jarId) return false
    if (className != other.className) return false
    if (methodName != other.methodName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + jarId.hashCode()
    result = 31 * result + className.hashCode()
    result = 31 * result + methodName.hashCode()
    return result
  }

  companion object {
    /**
     * Example:
     * CodeClassMetadata(
     *   jarId = 1,
     *   className = "com.acme.OrderCalculator",
     *   methodName = "calculateTotal",
     *   methodSignature = "(Order, double) -> OrderTotal",
     *   inputParams = """[{"name":"order","type":"com.acme.Order"},{"name":"taxRate","type":"double"}]""",
     *   returnType = "com.acme.OrderTotal"
     * )
     */
  }
}

