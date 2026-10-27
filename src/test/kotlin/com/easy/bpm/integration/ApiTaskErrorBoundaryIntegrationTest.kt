package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

/** Real PostgreSQL/transaction tests. Worker callbacks are delivered directly; live HTTP/broker coverage is in qa/api-error-handler. */
class ApiTaskErrorBoundaryIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var publisher: RabbitPublisher
    @Autowired lateinit var processes: ProcessService
    @Autowired lateinit var tasks: TaskService
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var events: ProcessInstanceEventRepository
    @Autowired lateinit var incidents: IncidentRepository
    @Autowired lateinit var taskRepository: TaskRepository
    @Autowired lateinit var mapper: ObjectMapper

    private fun start(boundary: Boolean = true, attachment: String = "api", legacy: Boolean = false, outputs: String? = null): Long {
        val key="api_error_${UUID.randomUUID()}"
        val definition=mapper.readTree("""{
          "processId":"$key",
          "variables":[{"name":"result","initialValue":"unchanged"},{"name":"count","initialValue":-1},{"name":"enabled","initialValue":true},{"name":"nothing","initialValue":"old"}],
          "nodes":[{"id":"start","type":"StartEvent","next":["api"]},
          {"id":"api","type":"APITask","next":["success"],"properties":{"url":"http://synthetic.invalid/qa","method":"GET","outputs":[
          {"sourceName":"nested","target":"variable","value":"result"},{"sourceName":"count","target":"variable","value":"count"},
          {"sourceName":"enabled","target":"variable","value":"enabled"},{"sourceName":"nothing","target":"variable","value":"nothing"}]}},
          {"id":"success","type":"EndEvent"},
          {"id":"recover","type":"HumanTask","next":["recovered"],"config":{"assignee":"alice","inputs":[],"outputs":[]}},
          {"id":"recovered","type":"EndEvent"}${if(boundary) ",{\"id\":\"boundary\",\"type\":\"ErrorBoundaryEvent\",\"attachedTo\":\"$attachment\",\"config\":{\"errorCode\":\"QA_ERROR\",\"exceptionVariable\":\"caughtError\"},\"next\":[\"recover\"]}" else ""}],
          "flows":[{"from":"start","to":"api"},{"from":"api","to":"success"},{"from":"recover","to":"recovered"}${if(boundary) ",{\"from\":\"boundary\",\"to\":\"recover\"}" else ""}]
        }""")
        val api=definition.path("nodes").first { it.path("id").asText()=="api" } as com.fasterxml.jackson.databind.node.ObjectNode
        outputs?.let { (api.path("properties") as com.fasterxml.jackson.databind.node.ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("outputs",mapper.readTree(it)) }
        if (legacy) api.set<com.fasterxml.jackson.databind.JsonNode>("service",api.remove("properties"))
        val deployed=processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val id=processes.startProcessInstance(deployed.id).id
        assertEquals(ProcessStatus.ACTIVE,instances.findById(id).orElseThrow().status)
        assertEquals(listOf("api"),instances.findById(id).orElseThrow().currentNode)
        return id
    }

    @Test fun `successful callback preserves nested JSON zero false null and skips boundary`() {
        val id=start()
        processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to """{"nested":{"items":[0,false,null]},"count":0,"enabled":false,"nothing":null}"""))
        val saved=processes.getProcessVariables(id).associate { it.name to it.value }
        assertEquals(mapper.readTree("""{"items":[0,false,null]}"""),saved["result"])
        assertEquals(0,saved["count"]!!.asInt());assertFalse(saved["enabled"]!!.asBoolean());assertTrue(saved["nothing"]!!.isNull)
        assertEquals(ProcessStatus.COMPLETED,instances.findById(id).orElseThrow().status)
        assertTrue(taskRepository.findByProcessInstanceId(id).isEmpty());assertEquals(0,incidents.countByProcessInstanceId(id))
    }

    @ParameterizedTest @ValueSource(strings=["HTTP 400","HTTP 401","HTTP 403","HTTP 404","HTTP 429","HTTP 500","HTTP 503","connection refused","read timeout"])
    fun `failure callback routes once to manual recovery without successful outputs`(reason: String) {
        val id=start()
        processes.handleServiceTaskFailed(id,"api",reason)
        val instance=instances.findById(id).orElseThrow()
        assertEquals(ProcessStatus.ACTIVE,instance.status);assertEquals(listOf("recover"),instance.currentNode)
        assertFalse(instance.nodeHistory.contains("success"));assertEquals(0,incidents.countByProcessInstanceId(id))
        val caught=processes.getProcessVariables(id).single { it.name=="caughtError" }.value.asText()
        assertTrue(caught.isNotBlank());assertFalse(caught.contains("SENSITIVE_MARKER"))
        assertEquals(-1,processes.getProcessVariables(id).single { it.name=="count" }.value.asInt())
        val recovery=taskRepository.findByProcessInstanceId(id).single()
        assertEquals("recover",recovery.nodeId);assertEquals(TaskStatus.PENDING,recovery.status)
        assertEquals(1,events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(id).count { it.eventType==ProcessInstanceEventType.WORKER_FAILED })
        tasks.completeTask(recovery.id,"alice",mapOf("resolution" to "Handled"))
        val final=instances.findById(id).orElseThrow();assertEquals(ProcessStatus.COMPLETED,final.status)
        assertTrue(final.nodeHistory.contains("recovered"));assertFalse(final.nodeHistory.contains("success"))
    }

    @Test fun `unhandled API failure persists failed node and one incident`() {
        val id=start(false)
        processes.handleServiceTaskFailed(id,"api","Synthetic unavailable API")
        val instance=instances.findById(id).orElseThrow()
        assertEquals(ProcessStatus.FAILED,instance.status);assertEquals("api",instance.errorNodeId)
        assertEquals(1,incidents.countByProcessInstanceId(id));assertTrue(taskRepository.findByProcessInstanceId(id).isEmpty())
        assertFalse(instance.nodeHistory.contains("success"))
    }

    @Test fun `boundary attached to another node cannot capture API failure`() {
        val id=start(attachment="start")
        processes.handleServiceTaskFailed(id,"api","Synthetic failure")
        assertEquals(ProcessStatus.FAILED,instances.findById(id).orElseThrow().status)
        assertTrue(taskRepository.findByProcessInstanceId(id).isEmpty());assertEquals(1,incidents.countByProcessInstanceId(id))
    }
    @Test fun `duplicate failure creates only one recovery task and one failure event`() {
        val id=start()
        processes.handleServiceTaskFailed(id,"api","first failure")
        val before=events.countByProcessInstanceId(id)
        processes.handleServiceTaskFailed(id,"api","replayed failure")
        assertEquals(1,taskRepository.findByProcessInstanceId(id).size)
        assertEquals(before,events.countByProcessInstanceId(id))
        assertEquals(listOf("recover"),instances.findById(id).orElseThrow().currentNode)
    }

    @Test fun `late success and repeated failure cannot resume FAILED instance`() {
        val id=start(false)
        processes.handleServiceTaskFailed(id,"api","unhandled")
        val before=events.countByProcessInstanceId(id)
        processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to """{"count":777}"""))
        processes.handleServiceTaskFailed(id,"api","replayed")
        assertEquals(ProcessStatus.FAILED,instances.findById(id).orElseThrow().status)
        assertEquals(before,events.countByProcessInstanceId(id));assertEquals(1,incidents.countByProcessInstanceId(id))
        assertEquals(-1,processes.getProcessVariables(id).single { it.name=="count" }.value.asInt())
    }

    @Test fun `callbacks cannot mutate COMPLETED or CANCELLED instances`() {
        for (status in listOf(ProcessStatus.COMPLETED,ProcessStatus.CANCELLED)) {
            val id=start()
            val instance=instances.findById(id).orElseThrow();instance.status=status;instances.saveAndFlush(instance)
            val before=events.countByProcessInstanceId(id)
            processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to """{"count":777}"""))
            processes.handleServiceTaskFailed(id,"api","late failure")
            assertEquals(status,instances.findById(id).orElseThrow().status)
            assertEquals(before,events.countByProcessInstanceId(id));assertTrue(taskRepository.findByProcessInstanceId(id).isEmpty())
        }
    }

    @Test fun `callback for an inactive node is ignored without mutation`() {
        val id=start()
        val before=events.countByProcessInstanceId(id)
        processes.handleServiceTaskCompleted(id,"not-active",emptyMap())
        processes.handleServiceTaskFailed(id,"not-active","late failure")
        assertEquals(listOf("api"),instances.findById(id).orElseThrow().currentNode)
        assertEquals(before,events.countByProcessInstanceId(id))
    }

    @Test fun `worker boundary captures safe error classification without response body`() {
        val id=start()
        processes.handleServiceTaskFailed(id,"api","500 Server Error: SENSITIVE_MARKER secret response")
        val caught=processes.getProcessVariables(id).single { it.name=="caughtError" }.value.asText()
        assertEquals("API request failed with HTTP 500.",caught)
    }

    @Test fun `human completion dispatches API asynchronously and downstream failure uses boundary`() {
        val definition=mapper.readTree("""{"processId":"human_api_${UUID.randomUUID()}","variables":[],"nodes":[
          {"id":"start","type":"StartEvent","next":["review"]},
          {"id":"review","type":"HumanTask","next":["api"],"config":{"assignee":"alice","outputs":[]}},
          {"id":"api","type":"APITask","next":["success"],"properties":{"url":"http://must-not-call.invalid","method":"GET","outputs":[]}},
          {"id":"success","type":"EndEvent"},{"id":"boundary","type":"ErrorBoundaryEvent","attachedTo":"api","config":{"exceptionVariable":"caughtError"},"next":["recover"]},
          {"id":"recover","type":"HumanTask","config":{"assignee":"alice"}}],"flows":[{"from":"start","to":"review"},{"from":"review","to":"api"},{"from":"api","to":"success"},{"from":"boundary","to":"recover"}]}""")
        val deployed=processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val id=processes.startProcessInstance(deployed.id).id
        val review=taskRepository.findByProcessInstanceId(id).single()
        tasks.completeTask(review.id,"alice",mapOf("humanComment" to "saved","approved" to false,"amount" to 0))
        assertEquals(TaskStatus.COMPLETED,taskRepository.findById(review.id).orElseThrow().status)
        assertEquals(listOf("api"),instances.findById(id).orElseThrow().currentNode)
        assertEquals(ProcessStatus.ACTIVE,instances.findById(id).orElseThrow().status)
        assertEquals(1,events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(id).count { it.eventType==ProcessInstanceEventType.WORKER_REQUESTED })
        processes.handleServiceTaskFailed(id,"api","503 unavailable")
        assertEquals(listOf("recover"),instances.findById(id).orElseThrow().currentNode)
        assertEquals("saved",tasks.getTaskResponseById(review.id)!!.variables["humanComment"])
        assertEquals(0,incidents.countByProcessInstanceId(id))
    }

    @Test fun `legacy service config applies outputs with original JSON types`() {
        val id=start(legacy=true)
        processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to """{"count":0,"enabled":false,"nothing":null,"nested":{"ok":true}}"""))
        val values=processes.getProcessVariables(id).associate { it.name to it.value }
        assertEquals(0,values["count"]!!.asInt());assertFalse(values["enabled"]!!.asBoolean());assertTrue(values["nothing"]!!.isNull)
    }

    @ParameterizedTest @ValueSource(strings=["number","boolean","json","string"])
    fun `static output mappings preserve declared types`(type: String) {
        val literal=when(type){"number"->"0";"boolean"->"false";"json"->"{\"items\":[0,false,null]}";else->"0"}
        val output=mapper.createObjectNode().put("source","static").put("sourceValue",literal).put("type",type).put("target","variable").put("targetVariable","result")
        val id=start(outputs=mapper.createArrayNode().add(output).toString())
        processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to "{}"))
        val result=processes.getProcessVariables(id).single { it.name=="result" }.value
        if(type=="string") {assertTrue(result.isTextual);assertEquals("0",result.asText())}
        else assertEquals(mapper.readTree(literal),result)
    }

    @Test fun `invalid static mapping routes to recovery without partial outputs`() {
        val id=start(outputs="""[{"sourceName":"count","target":"variable","value":"count"},{"source":"static","sourceValue":"wrong","type":"number","target":"variable","value":"result"}]""")
        processes.handleServiceTaskCompleted(id,"api",mapOf("__response" to """{"count":999}"""))
        assertEquals(listOf("recover"),instances.findById(id).orElseThrow().currentNode)
        assertEquals(-1,processes.getProcessVariables(id).single { it.name=="count" }.value.asInt())
        assertFalse(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(id).any { it.eventType==ProcessInstanceEventType.WORKER_COMPLETED })
    }

    @Test fun `concurrent duplicate failures consume active API token once`() {
        val id=start()
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(2);val go=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=(1..2).map { pool.submit { ready.countDown();go.await();processes.handleServiceTaskFailed(id,"api","duplicate") } }
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));go.countDown()
            jobs.forEach { it.get(15,java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(1,taskRepository.findByProcessInstanceId(id).size)
            assertEquals(1,events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(id).count { it.eventType==ProcessInstanceEventType.WORKER_FAILED })
        } finally { go.countDown();pool.shutdownNow() }
    }

}
