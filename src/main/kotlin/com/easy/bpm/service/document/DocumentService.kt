package com.easy.bpm.service.document

import com.easy.bpm.controller.data.DocumentResponseDto
import com.easy.bpm.model.document.Document
import com.easy.bpm.repository.document.DocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * DocumentService — handles upload, retrieval, streaming, and deletion of form documents.
 *
 * Validation rules (all enforced at upload time):
 * - File must not be empty
 * - File size must not exceed [MAX_FILE_SIZE_BYTES] (20 MB default)
 * - Content type must be in the [ALLOWED_CONTENT_TYPES] allowlist
 * - File name is sanitized to prevent path traversal
 */
@Service
class DocumentService(
    private val documentRepository: DocumentRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_FILE_SIZE_BYTES: Long = 20 * 1024 * 1024 // 20 MB

        val ALLOWED_CONTENT_TYPES: Set<String> = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "text/plain"
        )

        val ALLOWED_EXTENSIONS: Set<String> = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg", "txt"
        )
    }

    /**
     * Upload and persist a document, optionally associating it with a task, process instance, and form field.
     *
     * @throws IllegalArgumentException for validation failures
     */
    @Transactional
    fun upload(
        file: MultipartFile,
        taskId: Long? = null,
        processInstanceId: Long? = null,
        formFieldKey: String? = null,
        uploadedBy: String? = null
    ): DocumentResponseDto {
        validateFile(file)

        val sanitizedName = sanitizeFileName(file.originalFilename ?: "upload")
        val contentType = file.contentType ?: "application/octet-stream"

        // If a file already exists for this task + form field, remove the old one first (replace semantics)
        if (taskId != null && !formFieldKey.isNullOrBlank()) {
            val existing = documentRepository.findByTaskIdAndFormFieldKey(taskId, formFieldKey)
            if (existing.isNotEmpty()) {
                documentRepository.deleteAll(existing)
                logger.info("Replaced {} existing document(s) for task={} field={}", existing.size, taskId, formFieldKey)
            }
        }

        val document = Document(
            fileName = sanitizedName,
            contentType = contentType,
            fileSize = file.size,
            content = file.bytes,
            taskId = taskId,
            processInstanceId = processInstanceId,
            formFieldKey = formFieldKey,
            uploadedBy = uploadedBy
        )

        val saved = documentRepository.save(document)
        logger.info("Document saved id={} name={} size={} task={}", saved.id, saved.fileName, saved.fileSize, taskId)
        return toDto(saved)
    }

    /**
     * Retrieve document metadata (no binary content).
     */
    fun getMetadata(id: UUID): DocumentResponseDto {
        val doc = findOrThrow(id)
        return toDto(doc)
    }

    /**
     * Return raw bytes for download (attachment).
     */
    @Transactional(readOnly = true)
    fun getContent(id: UUID): Document {
        return findOrThrow(id)
    }

    /**
     * List all documents associated with a given task.
     */
    @Transactional(readOnly = true)
    fun listByTask(taskId: Long): List<DocumentResponseDto> =
        documentRepository.findByTaskId(taskId).map { toDto(it) }

    /**
     * Delete a document by ID.
     */
    @Transactional
    fun delete(id: UUID) {
        if (!documentRepository.existsById(id)) {
            throw IllegalArgumentException("Document not found: $id")
        }
        documentRepository.deleteById(id)
        logger.info("Document deleted id={}", id)
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("Uploaded file is empty")
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(
                "File size ${file.size} exceeds the maximum allowed size of $MAX_FILE_SIZE_BYTES bytes (20 MB)"
            )
        }

        val contentType = file.contentType ?: ""
        if (contentType !in ALLOWED_CONTENT_TYPES) {
            throw IllegalArgumentException(
                "Content type '$contentType' is not allowed. Permitted types: $ALLOWED_CONTENT_TYPES"
            )
        }

        val originalName = file.originalFilename ?: ""
        val extension = originalName.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in ALLOWED_EXTENSIONS) {
            throw IllegalArgumentException(
                "File extension '.$extension' is not allowed. Permitted extensions: $ALLOWED_EXTENSIONS"
            )
        }
    }

    /**
     * Prevent path traversal attacks by stripping directory separators and normalizing the name.
     */
    internal fun sanitizeFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "upload" }

    private fun findOrThrow(id: UUID): Document =
        documentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Document not found: $id") }

    private fun toDto(doc: Document) = DocumentResponseDto(
        id = doc.id!!,
        fileName = doc.fileName,
        contentType = doc.contentType,
        fileSize = doc.fileSize,
        taskId = doc.taskId,
        processInstanceId = doc.processInstanceId,
        formFieldKey = doc.formFieldKey,
        uploadedBy = doc.uploadedBy,
        createdAt = doc.createdAt
    )
}
