package com.easy.bpm.integration

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.security.AppPermissions
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.http.MediaType

@AutoConfigureMockMvc
@TestPropertySource(properties = ["easybpm.security.enabled=true"])
class DecisionSecurityIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbit: RabbitPublisher
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Test fun `real filter chain protects authors and operator histories`() {
        val paths = listOf("/decisions", "/decisions/key/versions", "/decisions/key/tests", "/decisions/key/diff?fromVersion=1&toVersion=2", "/decision-executions", "/decision-executions/1")
        paths.forEach { path ->
            mvc.perform(get(path)).andExpect(status().isUnauthorized)
            listOf(AppPermissions.ACCESS_PROCESS_PORTAL, "SCOPE_PROCESSES_READ", "unrelated").forEach { authority -> mvc.perform(get(path).with(user("test").authorities(SimpleGrantedAuthority(authority)))).andExpect(status().isForbidden) }
        }
        val author = user("modeler").authorities(SimpleGrantedAuthority(AppPermissions.ACCESS_BPM_MODELER))
        val admin = user("admin").authorities(SimpleGrantedAuthority(AppPermissions.ACCESS_BPM_ADMIN))
        mvc.perform(get("/decisions").with(author)).andExpect(status().isOk)
        mvc.perform(get("/decision-executions").with(author)).andExpect(status().isForbidden)
        mvc.perform(get("/decision-executions").with(admin)).andExpect(status().isOk)
        val xml = java.io.File("docs/examples/expense-approval.dmn").readText()
        val body = mapper.writeValueAsString(mapOf("dmnXml" to xml))
        val postPaths = listOf("/decisions", "/decisions/validate", "/decisions/test", "/decisions/key/evaluate", "/decisions/key/tests", "/decisions/key/tests/run")
        postPaths.forEach { path ->
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
            mvc.perform(post(path).with(user("portal").authorities(SimpleGrantedAuthority(AppPermissions.ACCESS_PROCESS_PORTAL))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden)
        }
        mvc.perform(post("/decisions/validate").with(author).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk)
        mvc.perform(post("/decisions").with(author).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated)
        mvc.perform(get("/decision-executions?size=101").with(admin)).andExpect(status().isBadRequest)
    }
    @Test fun `native bearer clients cannot access decision endpoints`() {
        val login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin\"}")).andExpect(status().isOk).andReturn()
        val token = mapper.readTree(login.response.contentAsString).path("token").asText()
        val created = mvc.perform(post("/admin/api-clients").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("name" to "DMN test ${java.util.UUID.randomUUID()}", "scopes" to listOf("processes:read"), "expiresAt" to java.time.LocalDateTime.now().plusDays(1).withNano(0).toString())))).andExpect(status().isCreated).andReturn()
        val credential = mapper.readTree(created.response.contentAsString).path("credential").asText()
        mvc.perform(get("/decisions").header("Authorization", "Bearer $credential")).andExpect(status().isForbidden)
        mvc.perform(get("/decision-executions").header("Authorization", "Bearer $credential")).andExpect(status().isForbidden)
        mvc.perform(post("/decisions/validate").header("Authorization", "Bearer $credential").contentType(MediaType.APPLICATION_JSON).content("{\"dmnXml\":\"\"}")).andExpect(status().isForbidden)
    }
    @Test fun `HTTP boundary preserves high precision decimal inputs`() {
        val xml = java.io.File("docs/examples/expense-approval.dmn").readText().replace("&lt; 1000", "&lt; 0.1000000000000000002").replace("&gt;= 1000", "&gt;= 0.1000000000000000002")
        val body = "{\"dmnXml\":" + mapper.writeValueAsString(xml) + ",\"inputs\":{\"amount\":0.1000000000000000003}}"
        mvc.perform(post("/decisions/test").with(user("author").authorities(SimpleGrantedAuthority(AppPermissions.ACCESS_BPM_MODELER))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk).andExpect(jsonPath("$.outputs.approval").value("REVIEW"))
    }

    @Test fun `incident recovery remains restricted to operators`() {
        mvc.perform(post("/incidents/999999999/retry")).andExpect(status().isUnauthorized)
        for (authority in listOf(AppPermissions.ACCESS_BPM_MODELER, AppPermissions.ACCESS_PROCESS_PORTAL)) {
            mvc.perform(post("/incidents/999999999/retry").with(user("author").authorities(SimpleGrantedAuthority(authority)))).andExpect(status().isForbidden)
        }
        mvc.perform(post("/incidents/999999999/retry").with(user("operator").authorities(SimpleGrantedAuthority(AppPermissions.ACCESS_BPM_ADMIN)))).andExpect(status().isNotFound)
    }
}
