package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.*
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.process.*
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.service.task.TaskSlaService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.http.MediaType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
@TestPropertySource(properties=["easybpm.security.enabled=true","easybpm.sla.poll-ms=30000"])
class TaskSlaApiIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbit: RabbitPublisher
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var tasks: TaskRepository
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var sla: TaskSlaService
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var manager: PlatformTransactionManager
    private fun identity(name: String, admin: Boolean=false, native: Boolean=false) = AuthenticatedUser(null,name,"",true,emptySet(),if(admin) setOf("ACCESS_BPM_ADMIN") else if(native) emptySet() else setOf("ACCESS_PROCESS_PORTAL"),if(native) setOf("tasks:read") else emptySet())
    @Test fun `API requires auth respects task visibility native scopes admin aggregates and returns UTC fields`() {
        val owner="api-owner-${UUID.randomUUID()}"
        val task=TransactionTemplate(manager).execute {
            val definition=definitions.saveAndFlush(ProcessDefinition(key="sla-api-${UUID.randomUUID()}",definitionJson="<xml/>"))
            val instance=instances.saveAndFlush(ProcessInstance(processDefinition=definition,status=ProcessStatus.ACTIVE))
            val task=tasks.saveAndFlush(Task(processInstanceId=instance.id,nodeId="review",assignee=owner))
            sla.configure(task,mapper.readTree("""{"config":{"sla":{"duration":"PT4H"}}}"""),Instant.parse("2026-01-01T00:00:00Z"));task
        }!!
        val body="""{"filters":[{"field":"processInstanceId","operator":"EQUALS","value":${task.processInstanceId}}]}"""
        mvc.perform(post("/tasks/sla/summary").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(post("/tasks/sla/summary").with(user(identity(owner))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk).andExpect(jsonPath("$.overdue").value(1))
        mvc.perform(post("/tasks/sla/summary").with(user(identity("outsider"))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk).andExpect(jsonPath("$.overdue").value(0))
        mvc.perform(post("/tasks/sla/summary").with(user(identity("service",native=true))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk).andExpect(jsonPath("$.overdue").value(0))
        mvc.perform(get("/tasks/${task.id}/sla/events").with(user(identity("outsider")))).andExpect(status().isForbidden)
        mvc.perform(get("/tasks/${task.id}/sla/events").with(user(identity(owner)))).andExpect(status().isOk).andExpect(jsonPath("$.content[0].dueDate").value("2026-01-01T04:00:00Z"))
        mvc.perform(get("/tasks/${task.id}").with(user(identity(owner)))).andExpect(status().isOk).andExpect(jsonPath("$.dueDate").value("2026-01-01T04:00:00Z")).andExpect(jsonPath("$.slaStatus").value("OVERDUE"))
        mvc.perform(get("/tasks/999999999/sla/events").with(user(identity(owner)))).andExpect(status().isNotFound)
        mvc.perform(get("/admin/sla/metrics").with(user(identity(owner)))).andExpect(status().isForbidden)
        mvc.perform(get("/admin/sla/metrics").with(user(identity("service",native=true)))).andExpect(status().isForbidden)
        mvc.perform(get("/admin/sla/metrics").with(user(identity("operator",admin=true)))).andExpect(status().isOk)
        mvc.perform(post("/incidents/999999999/retry").with(user(identity(owner))).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden)
        mvc.perform(post("/incidents/999999999/retry").with(user(identity("operator",admin=true))).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isNotFound)
        mvc.perform(post("/tasks/search").with(user(identity(owner))).contentType(MediaType.APPLICATION_JSON).content("""{"filters":[{"field":"dueDate","operator":"EQUALS","value":"2026-01-01T00:00:00"}]}""")).andExpect(status().isBadRequest)
        mvc.perform(post("/tasks/search").with(user(identity(owner))).contentType(MediaType.APPLICATION_JSON).content("""{"filters":[{"field":"slaStatus","operator":"CONTAINS","value":"OVERDUE"}]}""")).andExpect(status().isBadRequest)
    }
}
