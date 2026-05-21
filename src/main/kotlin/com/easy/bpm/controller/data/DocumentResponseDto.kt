package com.easy.bpm.controller.data

import java.time.LocalDateTime
import java.util.UUID

/**
 * Response DTO for document metadata.
 * Content bytes are NOT included — use the download/preview endpoints for binary content.
 */
data class DocumentResponseDto(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val taskId: Long?,
    val processInstanceId: Long?,
    val formFieldKey: String?,
    val uploadedBy: String?,
    val createdAt: LocalDateTime
)
