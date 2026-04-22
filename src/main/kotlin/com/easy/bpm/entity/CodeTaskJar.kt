package com.easy.bpm.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * CodeTaskJar - Represents an uploaded JAR file containing Java code
 *
 * Stores the actual JAR file content as BLOB along with metadata.
 * Hash-based deduplication prevents duplicate uploads.
 */
@Entity
@Table(
  name = "code_task_jar",
  indexes = [
    Index(name = "idx_code_task_jar_file_hash", columnList = "file_hash"),
    Index(name = "idx_code_task_jar_upload_date", columnList = "upload_date")
  ]
)
class CodeTaskJar(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "content", nullable = false, columnDefinition = "BYTEA")
  val content: ByteArray,

  @Column(name = "file_name", nullable = false, length = 255)
  val fileName: String,

  @Column(name = "file_hash", unique = true, nullable = false, length = 64)
  val fileHash: String,

  @Column(name = "upload_date", nullable = false)
  val uploadDate: LocalDateTime = LocalDateTime.now(),

  @Column(name = "uploaded_by", length = 255)
  val uploadedBy: String? = null,

  @Column(name = "description", columnDefinition = "TEXT")
  val description: String? = null
) {
  override fun toString(): String =
    "CodeTaskJar(id=$id, fileName='$fileName', fileHash='$fileHash', uploadDate=$uploadDate)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CodeTaskJar) return false
    if (id != other.id) return false
    if (fileHash != other.fileHash) return false
    return true
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + fileHash.hashCode()
    return result
  }
}

