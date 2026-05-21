package com.easy.bpm.controller

import com.easy.bpm.controller.data.DocumentResponseDto
import com.easy.bpm.service.DocumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * DocumentController — REST endpoints for form document handling.
 *
 * Base path: /api/documents
 *
 * Endpoints:
 *   POST   /api/documents                   — upload a file
 *   GET    /api/documents/{id}              — metadata only
 *   GET    /api/documents/{id}/download     — binary download (Content-Disposition: attachment)
 *   GET    /api/documents/{id}/preview      — inline binary (Content-Disposition: inline, PDF-safe)
 *   DELETE /api/documents/{id}              — delete document
 *   GET    /api/documents?taskId=X          — list documents for a task
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Form document upload, download, and preview")
class DocumentController(
    private val documentService: DocumentService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload a document", description = "Upload a file and associate it with a task, process instance and/or form field")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) taskId: Long?,
        @RequestParam(required = false) processInstanceId: Long?,
        @RequestParam(required = false) formFieldKey: String?,
        authentication: Authentication?
    ): ResponseEntity<DocumentResponseDto> {
        return try {
            val uploadedBy = authentication?.name
            val dto = documentService.upload(file, taskId, processInstanceId, formFieldKey, uploadedBy)
            ResponseEntity.status(HttpStatus.CREATED).body(dto)
        } catch (ex: IllegalArgumentException) {
            logger.warn("Document upload rejected: {}", ex.message)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata", description = "Returns document metadata without binary content")
    fun getMetadata(@PathVariable id: UUID): ResponseEntity<DocumentResponseDto> {
        return try {
            ResponseEntity.ok(documentService.getMetadata(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a document", description = "Returns the document content as an attachment")
    fun download(@PathVariable id: UUID): ResponseEntity<ByteArrayResource> {
        return try {
            val doc = documentService.getContent(id)
            val resource = ByteArrayResource(doc.content)
            ResponseEntity.ok()
                .headers {
                    it.contentDisposition = ContentDisposition.attachment()
                        .filename(doc.fileName)
                        .build()
                    it[HttpHeaders.CACHE_CONTROL] = "no-store"
                    it["X-Content-Type-Options"] = "nosniff"
                }
                .contentType(MediaType.parseMediaType(doc.contentType))
                .contentLength(doc.fileSize)
                .body(resource)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/preview")
    @Operation(
        summary = "Preview a document inline",
        description = "Returns the document content inline (suitable for PDF embed). Falls back to attachment for non-PDF types."
    )
    fun preview(@PathVariable id: UUID): ResponseEntity<ByteArrayResource> {
        return try {
            val doc = documentService.getContent(id)
            val resource = ByteArrayResource(doc.content)
            val isPdf = doc.contentType.equals("application/pdf", ignoreCase = true)
            val disposition = if (isPdf) {
                ContentDisposition.inline().filename(doc.fileName).build()
            } else {
                ContentDisposition.attachment().filename(doc.fileName).build()
            }
            ResponseEntity.ok()
                .headers {
                    it.contentDisposition = disposition
                    it[HttpHeaders.CACHE_CONTROL] = "no-store"
                    it["X-Content-Type-Options"] = "nosniff"
                }
                .contentType(MediaType.parseMediaType(doc.contentType))
                .contentLength(doc.fileSize)
                .body(resource)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document", description = "Permanently deletes a stored document")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        return try {
            documentService.delete(id)
            ResponseEntity.noContent().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    @Operation(summary = "List documents", description = "Returns document metadata list, optionally filtered by taskId")
    fun list(@RequestParam(required = false) taskId: Long?): ResponseEntity<List<DocumentResponseDto>> {
        if (taskId == null) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(documentService.listByTask(taskId))
    }
}
