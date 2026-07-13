package com.easy.bpm.model.document

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "documents",
    indexes = [
        Index(name = "idx_documents_task_id", columnList = "task_id"),
        Index(name = "idx_documents_instance_id", columnList = "process_instance_id"),
        Index(name = "idx_documents_uploaded_by", columnList = "uploaded_by"),
        Index(name = "idx_documents_created_at", columnList = "created_at")
    ]
)
data class Document(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "tenant_id", nullable = false, length = 100)
    val tenantId: String = "default",

    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    @Column(name = "content_type", nullable = false, length = 255)
    val contentType: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "content", nullable = false)
    val content: ByteArray,

    @Column(name = "task_id")
    val taskId: Long? = null,

    @Column(name = "process_instance_id")
    val processInstanceId: Long? = null,

    @Column(name = "form_field_key", length = 255)
    val formFieldKey: String? = null,

    @Column(name = "uploaded_by", length = 255)
    val uploadedBy: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    // Exclude mutable ByteArray from equality/hash to avoid content comparison overhead
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Document) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
