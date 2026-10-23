package com.easy.bpm.integration

import com.easy.bpm.decision.DecisionService
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.process.ProcessExecutionEngine
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class BusinessRuleTaskIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbit: RabbitPublisher
    @Autowired lateinit var decisions: DecisionService
    @Autowired lateinit var processes: ProcessService
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var engine: ProcessExecutionEngine
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var txManager: PlatformTransactionManager
    @Autowired lateinit var retry: com.easy.bpm.decision.DecisionIncidentRetryService
    private val created = mutableListOf<Long>()
    @AfterEach fun cleanup() { created.forEach { processes.deleteProcessInstance(it) }; created.clear() }
    private fun start(id: Long, values: Map<String, Any> = emptyMap()) = processes.startProcessInstance(id, values).also { created += it.id }
    private fun dmn(key: String, output: Boolean) = """<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="defs" namespace="urn:test"><decision id="$key" name="Eligibility"><decisionTable id="table" hitPolicy="UNIQUE"><input id="age"><inputExpression typeRef="number"><text>age</text></inputExpression></input><output id="approved" name="approved" typeRef="boolean"/><rule id="adult"><inputEntry><text>&gt;= 18</text></inputEntry><outputEntry><text>$output</text></outputEntry></rule></decisionTable></decision></definitions>"""
    private fun bpmn(key: String, exact: Int? = null, target: String = "approved") = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:easy="https://easybpm.local/bpmn/extensions"><process id="p_${UUID.randomUUID().toString().replace("-", "")}"><startEvent id="start"/><businessRuleTask id="rules"><extensionElements><easy:node><![CDATA[{"config":{"decisionKey":"$key"${exact?.let { ",\"decisionVersion\":$it" } ?: ""},"inputs":[{"targetName":"age","source":"variable","value":"customer.age","type":"number"}],"outputs":[{"sourceName":"$target","target":"variable","value":"approved","type":"boolean"}]}}]]></easy:node></extensionElements></businessRuleTask><userTask id="review"/><sequenceFlow id="f1" sourceRef="start" targetRef="rules"/><sequenceFlow id="f2" sourceRef="rules" targetRef="review"/></process></definitions>"""

    @Test fun `real process maps nested input advances and retains pinned version after deployment and reentry`() {
        val key = "rules_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val process = processes.deployProcess(bpmn(key))
        val instance = start(process.id, mapOf("customer" to mapOf("age" to 21)))
        assertEquals(listOf("review"), instances.findById(instance.id).orElseThrow().currentNode)
        assertEquals("true", jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='approved'", String::class.java, instance.id))
        decisions.deploy(dmn(key, false))
        TransactionTemplate(txManager).executeWithoutResult {
            val locked = instances.findByIdForUpdate(instance.id)!!
            engine.executeNodes(listOf("rules"), locked, BpmnXmlCodec.parseDefinition(process.definitionJson, mapper))
        }
        assertEquals(1, jdbc.queryForObject("select decision_version from decision_execution where process_instance_id=? order by id desc limit 1", Int::class.java, instance.id))
        assertEquals("true", jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='approved'", String::class.java, instance.id))
        val exact = processes.deployProcess(bpmn(key, 2))
        val newer = start(exact.id, mapOf("customer" to mapOf("age" to 21)))
        assertEquals("false", jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='approved'", String::class.java, newer.id))
    }

    @Test fun `nested output persists siblings and next decision reads updated path`() {
        val key = "nested_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val nextKey = "read_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(nextKey, true).replace("typeRef=\"number\"", "typeRef=\"boolean\"").replace("&gt;= 18", "true"))
        var source = bpmn(key).replace("\"value\":\"approved\"", "\"value\":\"customer.approved\"")
        val nextTask = bpmn(nextKey).substringAfter("<businessRuleTask").substringBefore("</businessRuleTask>")
            .replace("id=\"rules\"", "id=\"readBack\"").replace("customer.age", "customer.approved")
            .replace("\"type\":\"number\"", "\"type\":\"boolean\"")
        source = source.replace("<userTask id=\"review\"/>", "<businessRuleTask$nextTask</businessRuleTask><userTask id=\"review\"/>")
            .replace("targetRef=\"review\"", "targetRef=\"readBack\"")
            .replace("</process>", "<sequenceFlow id=\"f3\" sourceRef=\"readBack\" targetRef=\"review\"/></process>")
        val process = processes.deployProcess(source)
        val instance = start(process.id, mapOf("customer" to mapOf("age" to 21, "approved" to false, "name" to "Ana")))
        assertEquals(listOf("review"), instances.findById(instance.id).orElseThrow().currentNode)
        val customer = mapper.readTree(jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='customer'", String::class.java, instance.id))
        assertTrue(customer["approved"].asBoolean())
        assertEquals(21, customer["age"].asInt())
        assertEquals("Ana", customer["name"].asText())
        assertEquals("true", jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='approved'", String::class.java, instance.id))
        assertEquals(0L, jdbc.queryForObject("select count(*) from process_variable where process_instance_id=? and name='customer.approved'", Long::class.java, instance.id))
    }
    @Test fun `conflicting or nonobject output paths persist incident without partial roots`() {
        val key = "conflict_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val originalMapping = """{"sourceName":"approved","target":"variable","value":"approved","type":"boolean"}"""
        listOf(listOf("untouched", "customer", "customer.approved"), listOf("untouched", "customer.approved", "customer"), listOf("untouched", "scalar.child")).forEach { targets ->
            val mappings = targets.joinToString(",") { """{"sourceName":"approved","target":"variable","value":"$it","type":"boolean"}""" }
            val process = processes.deployProcess(bpmn(key).replace(originalMapping, mappings))
            val instance = start(process.id, mapOf("customer" to mapOf("age" to 21, "approved" to false), "scalar" to 1))
            assertEquals(ProcessStatus.FAILED, instances.findById(instance.id).orElseThrow().status)
            assertEquals(0L, jdbc.queryForObject("select count(*) from process_variable where process_instance_id=? and name='untouched'", Long::class.java, instance.id))
            assertEquals("false", jdbc.queryForObject("select value->>'approved' from process_variable where process_instance_id=? and name='customer'", String::class.java, instance.id))
            assertEquals(1L, jdbc.queryForObject("select count(*) from decision_execution where process_instance_id=? and status='FAILED'", Long::class.java, instance.id))
        }
    }
    @Test fun `first synchronous mapping failure persists failed instance audit incident and no output`() {
        val key = "failure_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val process = processes.deployProcess(bpmn(key, target = "missing"))
        val instance = start(process.id, mapOf("customer" to mapOf("age" to 21)))
        assertEquals(ProcessStatus.FAILED, instances.findById(instance.id).orElseThrow().status)
        assertEquals(1L, jdbc.queryForObject("select count(*) from incident where process_instance_id=? and node_id='rules'", Long::class.java, instance.id))
        assertEquals(1L, jdbc.queryForObject("select count(*) from decision_execution where process_instance_id=? and status='FAILED'", Long::class.java, instance.id))
        assertEquals("[\"adult\"]", jdbc.queryForObject("select matched_rule_ids_json from decision_execution where process_instance_id=? and status='FAILED'", String::class.java, instance.id))
        assertEquals(0L, jdbc.queryForObject("select count(*) from process_variable where process_instance_id=? and name='approved'", Long::class.java, instance.id))
    }

    @Test fun `documented DMN and BPMN example executes without modification`() {
        val decisionXml = java.io.File("docs/examples/expense-approval.dmn").readText()
        val processXml = java.io.File("docs/examples/expense-approval.bpmn").readText()
        decisions.deploy(decisionXml)
        val process = processes.deployProcess(processXml)
        val instance = start(process.id)
        assertEquals(ProcessStatus.COMPLETED, instances.findById(instance.id).orElseThrow().status)
        val execution = jdbc.queryForMap("select decision_key,status,matched_rule_ids_json from decision_execution where process_instance_id=?", instance.id)
        assertEquals("expenseApproval", execution["decision_key"])
        assertEquals("COMPLETED", execution["status"])
    }

    @Test fun `missing definition and no match persist diagnostic incidents instead of rolling back start`() {
        val absentKey = "absent_${UUID.randomUUID().toString().replace("-", "")}"
        val missingProcess = processes.deployProcess(bpmn(absentKey))
        val missing = start(missingProcess.id, mapOf("customer" to mapOf("age" to 21)))
        assertEquals(ProcessStatus.FAILED, instances.findById(missing.id).orElseThrow().status)
        assertEquals(1L, jdbc.queryForObject("select count(*) from decision_execution where process_instance_id=? and status='FAILED'", Long::class.java, missing.id))
        val key = "nomatch_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val process = processes.deployProcess(bpmn(key))
        val noMatch = start(process.id, mapOf("customer" to mapOf("age" to 10)))
        assertEquals(ProcessStatus.FAILED, instances.findById(noMatch.id).orElseThrow().status)
        assertEquals(1L, jdbc.queryForObject("select count(*) from incident where process_instance_id=?", Long::class.java, noMatch.id))
    }

    @Test fun `operator retry retains pin resolves incident and concurrent duplicate cannot advance again`() {
        val key = "retry_${UUID.randomUUID().toString().replace("-", "")}"
        decisions.deploy(dmn(key, true))
        val process = processes.deployProcess(bpmn(key))
        val instance = start(process.id, mapOf("customer" to mapOf("age" to "wrong-type")))
        val incident = jdbc.queryForObject("select id from incident where process_instance_id=?", Long::class.java, instance.id)!!
        assertEquals(ProcessStatus.FAILED, instances.findById(instance.id).orElseThrow().status)
        decisions.deploy(dmn(key, false))
        assertEquals(com.easy.bpm.model.incident.IncidentStatus.OPEN, retry.retry(incident, "operator").status)
        assertEquals(2, jdbc.queryForObject("select occurrence_count from incident where id=?", Int::class.java, incident))
        TransactionTemplate(txManager).executeWithoutResult {
            jdbc.update("update process_variable set value=?::jsonb where process_instance_id=? and name='customer'", "{\"age\":21}", instance.id)
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready = java.util.concurrent.CountDownLatch(1)
        try {
            val futures = (1..2).map { pool.submit<Boolean> { ready.await(); try { retry.retry(incident, "operator"); true } catch (e: org.springframework.web.server.ResponseStatusException) { assertEquals(409, e.statusCode.value()); false } } }
            ready.countDown()
            assertEquals(1, futures.count { it.get(20, java.util.concurrent.TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
        assertEquals("RESOLVED", jdbc.queryForObject("select status from incident where id=?", String::class.java, incident))
        assertEquals("true", jdbc.queryForObject("select value::text from process_variable where process_instance_id=? and name='approved'", String::class.java, instance.id))
        assertEquals(1L, jdbc.queryForObject("select count(*) from task where process_instance_id=? and node_id='review'", Long::class.java, instance.id))
        assertEquals(1, jdbc.queryForObject("select decision_version from decision_execution where process_instance_id=? and status='COMPLETED'", Int::class.java, instance.id))
    }
}
