package com.easy.bpm.integration

import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import com.easy.bpm.messaging.RabbitPublisher

@AutoConfigureMockMvc
@TestPropertySource(properties = ["easybpm.security.enabled=true"])
class SecurityIntegrationTest : IntegrationTestBase() {

    @MockitoBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var appUserRepository: AppUserRepository

    @Autowired
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var processDefinitionRepository: ProcessDefinitionRepository

    @Autowired
    private lateinit var processInstanceRepository: ProcessInstanceRepository

    @Test
    fun `unauthenticated access returns 401`() {
        mockMvc.perform(get("/tasks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login with bootstrap admin returns token`() {
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.username").value("admin"))
    }

    @Test
    fun `disabled users cannot authenticate`() {
        val user = appUserRepository.save(
            AppUser(
                username = "disabled-user",
                passwordHash = passwordEncoder.encode("secret123"),
                enabled = false,
                createdBy = "test",
                updatedBy = "test"
            )
        )

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"secret123"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `password is stored hashed`() {
        val raw = "myStrongPassword"
        val user = appUserRepository.save(
            AppUser(
                username = "hash-check",
                passwordHash = passwordEncoder.encode(raw),
                enabled = true,
                createdBy = "test",
                updatedBy = "test"
            )
        )

        assertNotEquals(raw, user.passwordHash)
        assertTrue(passwordEncoder.matches(raw, user.passwordHash))
    }

    @Test
    fun `user without task portal permission gets 403 on tasks`() {
        val permission = permissionRepository.findByCode("MANAGE_USERS")
            ?: permissionRepository.save(Permission(code = "MANAGE_USERS", name = "Manage users"))

        appUserRepository.save(
            AppUser(
                username = "limited-user",
                passwordHash = passwordEncoder.encode("secret123"),
                enabled = true,
                permissions = mutableSetOf(permission),
                createdBy = "test",
                updatedBy = "test"
            )
        )

        val tokenResponse = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"limited-user","password":"secret123"}""")
        ).andExpect(status().isOk)
            .andReturn().response.contentAsString

        val token = Regex("\"token\":\"([^\"]+)\"").find(tokenResponse)?.groupValues?.get(1)
            ?: throw IllegalStateException("Token not found")

        mockMvc.perform(
            get("/tasks")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `authorized user can load child hierarchy endpoint`() {
        val definition = processDefinitionRepository.save(
            ProcessDefinition(
                key = "security-hierarchy-test",
                processName = "Security Hierarchy Test",
                description = "Hierarchy endpoint security coverage",
                version = 1,
                definitionJson = "{}"
            )
        )

        val parent = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("start"),
                nodeHistory = listOf("start")
            )
        )

        val child = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("sub-start"),
                nodeHistory = listOf("sub-start"),
                parentInstanceId = parent.id,
                callActivityNodeId = "call-sub",
                nestingLevel = 1
            )
        )

        val tokenResponse = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        ).andExpect(status().isOk)
            .andReturn().response.contentAsString

        val token = Regex("\"token\":\"([^\"]+)\"").find(tokenResponse)?.groupValues?.get(1)
            ?: throw IllegalStateException("Token not found")

        mockMvc.perform(
            get("/processes/instances/${parent.id}/children")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(child.id))
    }
}

