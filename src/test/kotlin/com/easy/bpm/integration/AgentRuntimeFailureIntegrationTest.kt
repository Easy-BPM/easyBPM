package com.easy.bpm.integration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.easy.bpm.controller.ProcessController
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.agent.*
import com.easy.bpm.model.process.*
import com.easy.bpm.repository.agent.*
import com.easy.bpm.repository.process.*
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.service.process.ProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.InetSocketAddress
import java.util.UUID

class AgentRuntimeFailureIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var publisher: RabbitPublisher
    @Autowired lateinit var agents: AgentProcessDefinitionRepository
    @Autowired lateinit var executions: AgentProcessExecutionRepository
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var variables: ProcessVariableRepository
    @Autowired lateinit var incidents: IncidentRepository
    @Autowired lateinit var events: ProcessInstanceEventRepository
    @Autowired lateinit var controller: ProcessController
    @Autowired lateinit var service: ProcessService
    @Autowired lateinit var mapper: ObjectMapper
    private val marker="QA_SENSITIVE_MARKER_72"
    private lateinit var logs: ListAppender<ILoggingEvent>
    @BeforeEach fun capture() { logs=ListAppender();logs.start();(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).addAppender(logs) }
    @AfterEach fun release() { (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).detachAppender(logs);logs.stop() }

    private fun deploy(endpoint: String?, boundary: Boolean=false): String {
        val key="agent_${UUID.randomUUID()}"
        val agent=mapper.createObjectNode().put("goal",marker)
        if(endpoint!=null)agent.set<com.fasterxml.jackson.databind.JsonNode>("provider",mapper.createObjectNode().put("providerId","ollama").put("modelName","llama3.2").put("endpoint",endpoint).put("promptTemplate",marker))
        agents.save(AgentProcessDefinition(key=key,version=1,definitionJson=agent.toString()))
        val json=mapper.readTree("""{"processId":"$key","variables":[{"name":"decision","initialValue":"original"}],"nodes":[{"id":"start","type":"StartEvent","next":["agent"]},{"id":"agent","type":"AgentProcessCall","config":{"agentProcessKey":"$key","waitForCompletion":true,"timeoutValue":1,"timeoutUnit":"SECONDS","outputs":[{"source":"variable","sourceValue":"decision","targetVariable":"decision"}]},"next":["end"]},{"id":"end","type":"EndEvent"}],"flows":[{"from":"start","to":"agent"},{"from":"agent","to":"end"}]}""")
        if(boundary){
            (json["nodes"] as com.fasterxml.jackson.databind.node.ArrayNode).add(mapper.readTree("""{"id":"boundary","type":"ErrorBoundaryEvent","attachedTo":"agent","config":{"errorCode":"PROVIDER_FAILURE"},"next":["recover"]}"""))
            (json["nodes"] as com.fasterxml.jackson.databind.node.ArrayNode).add(mapper.readTree("""{"id":"recover","type":"UserTask","config":{"assignee":"alice"}}"""))
            (json["flows"] as com.fasterxml.jackson.databind.node.ArrayNode).add(mapper.readTree("""{"from":"boundary","to":"recover"}"""))
        }
        definitions.save(ProcessDefinition(key=key,version=1,definitionJson=legacyJsonFixtureToBpmnXml(json)))
        return key
    }
    private fun server(status: Int, delay: Long=0, action: (String)->Unit) {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/api/generate") { exchange ->
            exchange.requestBody.readAllBytes()
            if(delay>0)Thread.sleep(delay)
            val body=if(status==200) """{"response":"{\"decision\":\"ALLOW\"}","prompt_eval_count":1,"eval_count":1}""" else marker
            try { exchange.sendResponseHeaders(status,body.toByteArray().size.toLong());exchange.responseBody.use { it.write(body.toByteArray()) } } catch (_: java.io.IOException) {} finally { exchange.close() }
        }
        server.start();try{action("http://127.0.0.1:${server.address.port}")}finally{server.stop(0)}
    }
    private fun assertSafe() {
        val applicationLogs=logs.list.filter { it.loggerName.startsWith("com.easy.bpm") }
        assertFalse(applicationLogs.any { (it.formattedMessage+it.throwableProxy?.message).contains(marker) },"Sensitive marker must not appear in application logs")
    }
    private fun failed(endpoint: String, code: String) {
        val key=deploy(endpoint);val response=controller.startInstance(key)
        assertEquals(422,response.statusCode.value())
        val body=mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(response.body)
        assertEquals("error",body.path("status").asText());assertEquals(code,body.path("errorCode").asText())
        val instance=instances.findAll().single { it.processDefinition.key==key }
        assertEquals(instance.id,body.path("instanceId").asLong())
        assertEquals(ProcessStatus.FAILED,instance.status);assertEquals("agent",instance.errorNodeId)
        assertFalse(instance.nodeHistory.contains("end"))
        assertEquals("original",variables.findByProcessInstanceIdAndName(instance.id,"decision")!!.value.asText())
        val execution=executions.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id).single()
        assertEquals(AgentProcessExecutionStatus.FAILED,execution.status);assertNull(execution.outputPayload);assertNotNull(execution.completedAt)
        assertFalse(execution.decisionTrace.orEmpty().contains(marker))
        assertEquals(code,mapper.readTree(execution.decisionTrace).path("errorCode").asText())
        assertEquals(1,incidents.countByProcessInstanceId(instance.id));assertEquals("agent",incidents.findByProcessInstanceId(instance.id).single().nodeId)
        assertFalse(body.toString().contains(marker));assertFalse(instance.errorMessage.orEmpty().contains(marker))
        assertFalse(incidents.findByProcessInstanceId(instance.id).any { (it.message+it.technicalDetails).contains(marker) })
        assertFalse(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id).any { it.eventType==ProcessInstanceEventType.PROCESS_COMPLETED || it.eventType==ProcessInstanceEventType.AGENT_PROCESS_COMPLETED })
        assertSafe()
    }
    @Test fun `network failure commits failed process execution and safe incident`() = failed("http://127.0.0.1:1","NETWORK_ERROR")
    @Test fun `timeout commits controlled failure`() = server(200,2500) { failed(it,"TIMEOUT") }
    @Test fun `provider response body cannot leak into logs or incident`() = server(500) { failed(it,"PROVIDER_ERROR") }
    @Test fun `successful provider completes normally`() = server(200) { endpoint ->
        val key=deploy(endpoint);val response=controller.startInstance(key);assertEquals(200,response.statusCode.value())
        val instance=instances.findAll().single { it.processDefinition.key==key };assertEquals(ProcessStatus.COMPLETED,instance.status)
        assertEquals(AgentProcessExecutionStatus.COMPLETED,executions.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id).single().status)
        assertEquals(0,incidents.countByProcessInstanceId(instance.id))
        assertEquals("AGENT_PROCESS_DECIDED",service.getProcessVariables(instance.id).single { it.name=="decision" }.value.asText());assertSafe()
    }
    @Test fun `planning mode remains provider free and completes`() {
        val key=deploy(null);val response=controller.startInstance(key);assertEquals(200,response.statusCode.value())
        val instance=instances.findAll().single { it.processDefinition.key==key };assertEquals(ProcessStatus.COMPLETED,instance.status)
        assertEquals("AGENT_PROCESS_PLANNED",service.getProcessVariables(instance.id).single { it.name=="decision" }.value.asText())
    }
    @Test fun `error boundary still handles provider failure without transaction rollback`() {
        val key=deploy("http://127.0.0.1:1",true);val response=controller.startInstance(key);assertEquals(200,response.statusCode.value())
        val instance=instances.findAll().single { it.processDefinition.key==key };assertEquals(ProcessStatus.ACTIVE,instance.status);assertEquals(listOf("recover"),instance.currentNode)
        assertEquals(0,incidents.countByProcessInstanceId(instance.id));assertEquals(AgentProcessExecutionStatus.FAILED,executions.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id).single().status)
        assertEquals("original",variables.findByProcessInstanceIdAndName(instance.id,"decision")!!.value.asText());assertSafe()
    }
    @Test fun `HTTP start returns a safe persisted runtime error`() {
        val key=deploy("http://127.0.0.1:1")
        val mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/processes/{id}/start",key))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.instanceId").exists())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.instanceStatus").value("FAILED"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("NETWORK_ERROR"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.nodeId").value("agent"))
        assertEquals(1,instances.findAll().count { it.processDefinition.key==key });assertSafe()
    }

}
