package com.easy.bpm.service

import com.easy.bpm.model.document.Document
import com.easy.bpm.repository.document.DocumentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class DocumentServiceTest : FunSpec({

    val mockRepository = mockk<DocumentRepository>()
    val service = DocumentService(mockRepository)

    beforeEach { clearAllMocks() }

    // -------------------------------------------------------------------------
    // upload – validation
    // -------------------------------------------------------------------------

    context("upload validation") {

        test("should reject empty file") {
            val file = MockMultipartFile("file", "test.pdf", "application/pdf", ByteArray(0))

            shouldThrow<IllegalArgumentException> {
                service.upload(file)
            }.message shouldBe "Uploaded file is empty"
        }

        test("should reject file exceeding 20 MB") {
            val bigContent = ByteArray((20 * 1024 * 1024 + 1).toInt())
            val file = MockMultipartFile("file", "big.pdf", "application/pdf", bigContent)

            shouldThrow<IllegalArgumentException> {
                service.upload(file)
            }.message!!.contains("exceeds the maximum allowed size") shouldBe true
        }

        test("should reject disallowed content type") {
            val file = MockMultipartFile("file", "script.exe", "application/x-msdownload", ByteArray(100))

            shouldThrow<IllegalArgumentException> {
                service.upload(file)
            }.message!!.contains("not allowed") shouldBe true
        }

        test("should reject disallowed file extension") {
            val file = MockMultipartFile("file", "virus.bat", "text/plain", ByteArray(100))

            shouldThrow<IllegalArgumentException> {
                service.upload(file)
            }.message!!.contains("not allowed") shouldBe true
        }

        test("should accept valid PDF") {
            val content = ByteArray(1024) { it.toByte() }
            val file = MockMultipartFile("file", "report.pdf", "application/pdf", content)
            val savedDoc = makeDocument(content = content)

            every { mockRepository.findByTaskIdAndFormFieldKey(any(), any()) } returns emptyList()
            every { mockRepository.save(any()) } returns savedDoc

            val result = service.upload(file, taskId = 1L, formFieldKey = "supportingDoc")

            result shouldNotBe null
            result.fileName shouldBe savedDoc.fileName
            verify { mockRepository.save(any()) }
        }

        test("should accept valid DOCX") {
            val content = ByteArray(512)
            val file = MockMultipartFile(
                "file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content
            )
            val savedDoc = makeDocument(
                fileName = "contract.docx",
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content = content
            )

            every { mockRepository.findByTaskIdAndFormFieldKey(any(), any()) } returns emptyList()
            every { mockRepository.save(any()) } returns savedDoc

            service.upload(file, taskId = 2L, formFieldKey = "contract") shouldNotBe null
        }
    }

    // -------------------------------------------------------------------------
    // upload – replace semantics
    // -------------------------------------------------------------------------

    context("upload replace semantics") {

        test("should replace existing document when same taskId + formFieldKey") {
            val content = ByteArray(256)
            val file = MockMultipartFile("file", "new.pdf", "application/pdf", content)
            val existing = makeDocument()
            val savedDoc = makeDocument(fileName = "new.pdf", content = content)

            every { mockRepository.findByTaskIdAndFormFieldKey(1L, "doc") } returns listOf(existing)
            every { mockRepository.deleteAll(any()) } just runs
            every { mockRepository.save(any()) } returns savedDoc

            val result = service.upload(file, taskId = 1L, formFieldKey = "doc")

            verify { mockRepository.deleteAll(listOf(existing)) }
            result.fileName shouldBe "new.pdf"
        }

        test("should not attempt delete when no existing document") {
            val content = ByteArray(256)
            val file = MockMultipartFile("file", "first.pdf", "application/pdf", content)
            val savedDoc = makeDocument(fileName = "first.pdf", content = content)

            every { mockRepository.findByTaskIdAndFormFieldKey(1L, "doc") } returns emptyList()
            every { mockRepository.save(any()) } returns savedDoc

            service.upload(file, taskId = 1L, formFieldKey = "doc")

            verify(exactly = 0) { mockRepository.deleteAll(any<List<Document>>()) }
        }
    }

    // -------------------------------------------------------------------------
    // getMetadata
    // -------------------------------------------------------------------------

    context("getMetadata") {

        test("should return DTO for existing document") {
            val doc = makeDocument()
            every { mockRepository.findById(doc.id!!) } returns Optional.of(doc)

            val result = service.getMetadata(doc.id!!)

            result.id shouldBe doc.id
            result.fileName shouldBe doc.fileName
            result.fileSize shouldBe doc.fileSize
        }

        test("should throw when document not found") {
            val id = UUID.randomUUID()
            every { mockRepository.findById(id) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> { service.getMetadata(id) }
        }
    }

    // -------------------------------------------------------------------------
    // getContent
    // -------------------------------------------------------------------------

    context("getContent") {

        test("should return Document with bytes for streaming") {
            val content = ByteArray(512) { 0xFF.toByte() }
            val doc = makeDocument(content = content)
            every { mockRepository.findById(doc.id!!) } returns Optional.of(doc)

            val result = service.getContent(doc.id!!)

            result.content shouldBe content
        }

        test("should throw when document not found") {
            val id = UUID.randomUUID()
            every { mockRepository.findById(id) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> { service.getContent(id) }
        }
    }

    // -------------------------------------------------------------------------
    // listByTask
    // -------------------------------------------------------------------------

    context("listByTask") {

        test("should return all documents for a task") {
            val docs = listOf(makeDocument(taskId = 5L), makeDocument(taskId = 5L))
            every { mockRepository.findByTaskId(5L) } returns docs

            val result = service.listByTask(5L)

            result.size shouldBe 2
        }

        test("should return empty list when task has no documents") {
            every { mockRepository.findByTaskId(99L) } returns emptyList()

            service.listByTask(99L).size shouldBe 0
        }
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    context("delete") {

        test("should delete existing document") {
            val id = UUID.randomUUID()
            every { mockRepository.existsById(id) } returns true
            every { mockRepository.deleteById(id) } just runs

            service.delete(id) // should not throw

            verify { mockRepository.deleteById(id) }
        }

        test("should throw when document not found on delete") {
            val id = UUID.randomUUID()
            every { mockRepository.existsById(id) } returns false

            shouldThrow<IllegalArgumentException> { service.delete(id) }
        }
    }

    // -------------------------------------------------------------------------
    // sanitizeFileName
    // -------------------------------------------------------------------------

    context("sanitizeFileName") {

        test("should strip leading path on Unix paths") {
            service.sanitizeFileName("/etc/passwd") shouldBe "passwd"
        }

        test("should strip leading path on Windows paths") {
            service.sanitizeFileName("C:\\Windows\\System32\\file.exe") shouldBe "file.exe"
        }

        test("should return 'upload' for blank name") {
            service.sanitizeFileName("   ") shouldBe "upload"
        }

        test("should return name unchanged for simple filename") {
            service.sanitizeFileName("my-report.pdf") shouldBe "my-report.pdf"
        }
    }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun makeDocument(
    id: UUID = UUID.randomUUID(),
    fileName: String = "document.pdf",
    contentType: String = "application/pdf",
    fileSize: Long = 1024L,
    content: ByteArray = ByteArray(1024),
    taskId: Long? = 1L,
    processInstanceId: Long? = null,
    formFieldKey: String? = "doc",
    uploadedBy: String? = "test-user"
): Document = Document(
    id = id,
    fileName = fileName,
    contentType = contentType,
    fileSize = fileSize,
    content = content,
    taskId = taskId,
    processInstanceId = processInstanceId,
    formFieldKey = formFieldKey,
    uploadedBy = uploadedBy,
    createdAt = LocalDateTime.now()
)
