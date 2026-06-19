package com.easy.bpm.controller

import com.easy.bpm.controller.data.DocumentResponseDto
import com.easy.bpm.model.document.Document
import com.easy.bpm.service.DocumentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime
import java.util.UUID

class DocumentControllerTest : FunSpec() {
    init {

    val mockService = mockk<DocumentService>()
    val controller = DocumentController(mockService)

    beforeEach { clearAllMocks() }

    context("upload") {

        test("should return CREATED with metadata on successful upload") {
            val dto = makeDto()
            val file = MockMultipartFile("file", "report.pdf", "application/pdf", ByteArray(512))

            every { mockService.upload(file, null, null, null, null) } returns dto

            val response = controller.upload(file, null, null, null, null)

            response.statusCode shouldBe HttpStatus.CREATED
            response.body shouldNotBe null
            response.body?.id shouldBe dto.id
        }

        test("should return BAD_REQUEST when service throws IllegalArgumentException") {
            val file = MockMultipartFile("file", "bad.exe", "application/x-msdownload", ByteArray(100))

            every { mockService.upload(file, null, null, null, null) } throws
                IllegalArgumentException("Content type not allowed")

            val response = controller.upload(file, null, null, null, null)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    context("getMetadata") {

        test("should return OK with metadata for existing document") {
            val id = UUID.randomUUID()
            val dto = makeDto(id = id)
            every { mockService.getMetadata(id) } returns dto

            val response = controller.getMetadata(id)

            response.statusCode shouldBe HttpStatus.OK
            response.body?.id shouldBe id
        }

        test("should return NOT_FOUND when document does not exist") {
            val id = UUID.randomUUID()
            every { mockService.getMetadata(id) } throws IllegalArgumentException("Not found")

            val response = controller.getMetadata(id)

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    context("download") {

        test("should return OK with attachment content disposition for existing document") {
            val id = UUID.randomUUID()
            val doc = makeDocument(id = id)
            every { mockService.getContent(id) } returns doc

            val response = controller.download(id)

            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentDisposition.isAttachment shouldBe true
            response.headers.contentDisposition.filename shouldBe doc.fileName
        }

        test("should return NOT_FOUND when document missing") {
            val id = UUID.randomUUID()
            every { mockService.getContent(id) } throws IllegalArgumentException("Not found")

            controller.download(id).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    context("preview") {

        test("should return inline Content-Disposition for PDF") {
            val id = UUID.randomUUID()
            val doc = makeDocument(id = id, contentType = "application/pdf")
            every { mockService.getContent(id) } returns doc

            val response = controller.preview(id)

            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentDisposition.isInline shouldBe true
        }

        test("should return attachment Content-Disposition for non-PDF") {
            val id = UUID.randomUUID()
            val doc = makeDocument(id = id, contentType = "image/png", fileName = "photo.png")
            every { mockService.getContent(id) } returns doc

            val response = controller.preview(id)

            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentDisposition.isAttachment shouldBe true
        }

        test("should return NOT_FOUND when document missing") {
            val id = UUID.randomUUID()
            every { mockService.getContent(id) } throws IllegalArgumentException("Not found")

            controller.preview(id).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    context("delete") {

        test("should return NO_CONTENT on successful deletion") {
            val id = UUID.randomUUID()
            every { mockService.delete(id) } just runs

            controller.delete(id).statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("should return NOT_FOUND when document does not exist") {
            val id = UUID.randomUUID()
            every { mockService.delete(id) } throws IllegalArgumentException("Not found")

            controller.delete(id).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    context("list") {

        test("should return BAD_REQUEST when taskId is null") {
            controller.list(null).statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("should return OK with list for a given taskId") {
            val dtos = listOf(makeDto(), makeDto())
            every { mockService.listByTask(7L) } returns dtos

            val response = controller.list(7L)

            response.statusCode shouldBe HttpStatus.OK
            response.body?.size shouldBe 2
        }
    }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun makeDto(id: UUID = UUID.randomUUID()) = DocumentResponseDto(
    id = id,
    fileName = "document.pdf",
    contentType = "application/pdf",
    fileSize = 1024L,
    taskId = 1L,
    processInstanceId = null,
    formFieldKey = "doc",
    uploadedBy = "test-user",
    createdAt = LocalDateTime.now()
)

private fun makeDocument(
    id: UUID = UUID.randomUUID(),
    fileName: String = "document.pdf",
    contentType: String = "application/pdf"
) = Document(
    id = id,
    fileName = fileName,
    contentType = contentType,
    fileSize = 1024L,
    content = ByteArray(1024),
    taskId = 1L,
    processInstanceId = null,
    formFieldKey = "doc",
    uploadedBy = "test-user",
    createdAt = LocalDateTime.now()
)
