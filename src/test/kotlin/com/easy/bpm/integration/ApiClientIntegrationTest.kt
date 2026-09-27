package com.easy.bpm.integration

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.repository.security.ApiClientRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@AutoConfigureMockMvc
@TestPropertySource(properties = ["easybpm.security.enabled=true"])
class ApiClientIntegrationTest : IntegrationTestBase() {
    @MockitoBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var apiClientRepository: ApiClientRepository

    @Test
    fun `create authenticate rotate audit and revoke against PostgreSQL`() {
        val adminToken = loginAdmin()
        val createResult = mockMvc.perform(
            post("/admin/api-clients")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "PostgreSQL integration ${UUID.randomUUID()}",
                            "permissionCodes" to listOf("ACCESS_PROCESS_PORTAL"),
                            "expiresAt" to LocalDateTime.now().plusDays(30).withNano(0).toString()
                        )
                    )
                )
        ).andExpect(status().isCreated).andReturn()

        val created = objectMapper.readTree(createResult.response.contentAsString)
        val clientId = UUID.fromString(created.path("client").path("id").asText())
        val credential = created.path("credential").asText()
        val version = created.path("client").path("version").asLong()
        assertThat(credential).matches("^ebpm_[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{43}$")
        val persisted = apiClientRepository.findById(clientId).orElseThrow()
        assertThat(persisted.secretHash).doesNotContain(credential.substringAfter('.'))

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer $credential"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.identityType").value("API_CLIENT"))
            .andExpect(jsonPath("$.permissions[0]").value("ACCESS_PROCESS_PORTAL"))

        mockMvc.perform(get("/admin/api-clients").header("Authorization", "Bearer $credential"))
            .andExpect(status().isForbidden)

        val rotateResult = mockMvc.perform(
            post("/admin/api-clients/$clientId/rotate")
                .header("Authorization", "Bearer $adminToken")
                .header("If-Match", "\"$version\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isOk).andReturn()
        val rotated = objectMapper.readTree(rotateResult.response.contentAsString)
        val rotatedCredential = rotated.path("credential").asText()
        val rotatedVersion = rotated.path("client").path("version").asLong()

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer $credential"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer $rotatedCredential"))
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/admin/api-clients/$clientId/audit")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.action == 'CREATED')]").isNotEmpty)
            .andExpect(jsonPath("$.content[?(@.action == 'ROTATED')]").isNotEmpty)
            .andExpect(jsonPath("$.content[?(@.action == 'API_REQUEST')]").isNotEmpty)

        mockMvc.perform(
            post("/admin/api-clients/$clientId/revoke")
                .header("Authorization", "Bearer $adminToken")
                .header("If-Match", "\"$rotatedVersion\"")
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer $rotatedCredential"))
            .andExpect(status().isUnauthorized)
    }

    private fun loginAdmin(): String {
        val response = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(response).path("token").asText()
    }
}
