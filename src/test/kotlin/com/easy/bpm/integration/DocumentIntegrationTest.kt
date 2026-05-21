package com.easy.bpm.integration

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.repository.document.DocumentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@AutoConfigureMockMvc
@TestPropertySource(properties = ["easybpm.security.enabled=true"])
@Transactional
class DocumentIntegrationTest : IntegrationTestBase() {

    @MockitoBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    // -------------------------------------------------------------------------
    // Authentication helpers
    // -------------------------------------------------------------------------

    private fun loginAsAdmin(): String {
        val response = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"token\":\"([^\"]+)\"").find(response)?.groupValues?.get(1)
            ?: error("Token not found in login response")
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    @Test
    fun `upload should reject unauthenticated requests with 401`() {
        val file = MockMultipartFile("file", "report.pdf", "application/pdf", ByteArray(64))
        mockMvc.perform(multipart("/api/documents").file(file))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload should persist document and return 201 for authenticated user`() {
        val token = loginAsAdmin()
        val content = "PDF content".toByteArray()
        val file = MockMultipartFile("file", "test.pdf", "application/pdf", content)

        val result = mockMvc.perform(
            multipart("/api/documents")
                .file(file)
                .param("formFieldKey", "supportingDoc")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.fileName").value("test.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.fileSize").value(content.size))
            .andReturn()

        val idStr = Regex(""""id"\s*:\s*"([^"]+)"""").find(result.response.contentAsString)?.groupValues?.get(1)
        assertThat(idStr).isNotNull
        val uuid = UUID.fromString(idStr!!)
        assertThat(documentRepository.findById(uuid)).isPresent
    }

    @Test
    fun `upload should return 400 for disallowed content type`() {
        val token = loginAsAdmin()
        val file = MockMultipartFile("file", "script.exe", "application/x-msdownload", ByteArray(64))

        mockMvc.perform(
            multipart("/api/documents")
                .file(file)
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `upload should return 400 for empty file`() {
        val token = loginAsAdmin()
        val file = MockMultipartFile("file", "empty.pdf", "application/pdf", ByteArray(0))

        mockMvc.perform(
            multipart("/api/documents")
                .file(file)
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isBadRequest)
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    @Test
    fun `getMetadata should return 404 for unknown document`() {
        val token = loginAsAdmin()
        mockMvc.perform(
            get("/api/documents/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `getMetadata should return 200 with metadata for existing document`() {
        val token = loginAsAdmin()
        val id = uploadTestDocument(token, "meta-test.pdf")

        mockMvc.perform(
            get("/api/documents/$id")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.fileName").value("meta-test.pdf"))
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    @Test
    fun `download should return binary content with attachment disposition`() {
        val token = loginAsAdmin()
        val id = uploadTestDocument(token, "download-test.pdf")

        mockMvc.perform(
            get("/api/documents/$id/download")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("download-test.pdf")))
    }

    @Test
    fun `download should return 404 for unknown document`() {
        val token = loginAsAdmin()
        mockMvc.perform(
            get("/api/documents/${UUID.randomUUID()}/download")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

    @Test
    fun `preview should return inline disposition for PDF`() {
        val token = loginAsAdmin()
        val id = uploadTestDocument(token, "preview-test.pdf", "application/pdf")

        mockMvc.perform(
            get("/api/documents/$id/preview")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
    }

    @Test
    fun `preview should return attachment disposition for non-PDF (image)`() {
        val token = loginAsAdmin()
        val id = uploadTestDocument(token, "photo.png", "image/png")

        mockMvc.perform(
            get("/api/documents/$id/preview")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Test
    fun `delete should remove document and return 204`() {
        val token = loginAsAdmin()
        val id = uploadTestDocument(token, "to-delete.pdf")

        mockMvc.perform(
            delete("/api/documents/$id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        // Verify it is gone
        mockMvc.perform(
            get("/api/documents/$id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `delete should return 404 for unknown document`() {
        val token = loginAsAdmin()
        mockMvc.perform(
            delete("/api/documents/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------------------
    // List by task
    // -------------------------------------------------------------------------

    @Test
    fun `list should return 400 when taskId is not provided`() {
        val token = loginAsAdmin()
        mockMvc.perform(
            get("/api/documents")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `list should return empty array for task with no documents`() {
        val token = loginAsAdmin()
        mockMvc.perform(
            get("/api/documents?taskId=99999")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // -------------------------------------------------------------------------
    // Replace semantics
    // -------------------------------------------------------------------------

    @Test
    fun `upload should replace document for same task and formFieldKey`() {
        val token = loginAsAdmin()

        // Upload first document
        val file1 = MockMultipartFile("file", "v1.pdf", "application/pdf", "version 1".toByteArray())
        val res1 = mockMvc.perform(
            multipart("/api/documents")
                .file(file1)
                .param("taskId", "1")
                .param("formFieldKey", "agreement")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id1Str = Regex(""""id"\s*:\s*"([^"]+)"""").find(res1.response.contentAsString)?.groupValues?.get(1)
        assertThat(id1Str).isNotNull

        // Force commit so replace logic can see previous row outside transaction scope
        TestTransaction.flagForCommit()
        TestTransaction.end()
        TestTransaction.start()

        // Upload second document (replace)
        val file2 = MockMultipartFile("file", "v2.pdf", "application/pdf", "version 2".toByteArray())
        mockMvc.perform(
            multipart("/api/documents")
                .file(file2)
                .param("taskId", "1")
                .param("formFieldKey", "agreement")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isCreated)

        // Only one document should remain for this task + field
        val remaining = documentRepository.findByTaskIdAndFormFieldKey(1L, "agreement")
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].fileName).isEqualTo("v2.pdf")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun uploadTestDocument(
        token: String,
        filename: String,
        contentType: String = "application/pdf",
        content: ByteArray = "PDF content".toByteArray()
    ): UUID {
        val file = MockMultipartFile("file", filename, contentType, content)
        val result = mockMvc.perform(
            multipart("/api/documents")
                .file(file)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val idStr = Regex(""""id"\s*:\s*"([^"]+)"""").find(result.response.contentAsString)?.groupValues?.get(1)
            ?: error("Could not extract id from upload response")
        return UUID.fromString(idStr)
    }
}
