package com.easy.bpm.integration

import com.easy.bpm.decision.*
import com.easy.bpm.messaging.RabbitPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID
import java.util.concurrent.Executors

class DecisionIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbit: RabbitPublisher
    @Autowired lateinit var service: DecisionService
    @Autowired lateinit var definitions: DecisionDefinitionRepository
    @Autowired lateinit var executions: DecisionExecutionRepository
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var meters: MeterRegistry
    private fun xml(key: String) = java.io.File("docs/examples/expense-approval.dmn").readText().replace("expenseApproval\"", "$key\"")

    @Test fun `concurrent deployment versions are immutable and saved cases compare versions`() {
        val key = "test_${UUID.randomUUID().toString().replace("-", "")}"; val source = xml(key)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = (1..3).map { executor.submit<DecisionDefinition> { service.deploy(source) } }
            assertEquals(listOf(1, 2, 3), futures.map { it.get().version }.sorted())
        } finally { executor.shutdownNow() }
        assertEquals(source, service.resolve(key, 1).dmnXml)
        assertEquals(listOf(3, 2, 1), service.versions(key).map { it.version })
        val modified = source.replace("APPROVED", "OK")
        val fourth = service.deploy(modified)
        assertEquals(4, fourth.version)
        assertEquals(source, service.resolve(key, 1).dmnXml)
        assertTrue((service.diff(key, 1, 4)["changes"] as List<*>).isNotEmpty())
        service.saveTest(key, "small amount", mapOf("amount" to 10), mapOf("approval" to "APPROVED", "requiresReview" to false))
        assertEquals(1, service.savedTests(key).size)
        val oldResults = service.runTests(key, 1)["results"] as List<*>
        assertEquals(true, (oldResults.single() as Map<*, *>)["passed"])
        val newResults = service.runTests(key, 4)["results"] as List<*>
        assertEquals(false, (newResults.single() as Map<*, *>)["passed"])
        assertThrows(DecisionException::class.java) { service.resolve(key, 99) }
    }
    @Test fun `metadata only deployments remain visible in version diff`() {
        val key = "metadata_${UUID.randomUUID().toString().replace("-", "")}"
        val source = xml(key)
        service.deploy(source)
        service.deploy(source.replace("<dmn:decisionTable", "<dmn:description>Business docs</dmn:description><dmn:decisionTable"))
        val changes = service.diff(key, 1, 2)["changes"] as List<*>
        assertTrue(changes.any { (it as Map<*, *>)["path"] == "description" })
    }
    @Test fun `saved cases compare nested numbers by exact semantic value`() {
        val key = "numeric_${UUID.randomUUID().toString().replace("-", "")}"
        val source = """<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"><decision id="$key"><decisionTable><input id="i"><inputExpression typeRef="number"><text>age</text></inputExpression></input><output id="o" name="result" typeRef="Any"/><rule id="r"><inputEntry><text>-</text></inputEntry><outputEntry><text>{"nested":[1,1.0,9007199254740993,0.1000000000000000001]}</text></outputEntry></rule></decisionTable></decision></definitions>"""
        service.deploy(source)
        val reader = mapper.readerFor(Any::class.java).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        service.saveTest(key, "equivalent", mapOf("age" to 21), reader.readValue<Any>("""{"result":{"nested":[1.0,1,9007199254740993.0,0.1000000000000000001]}}"""))
        service.saveTest(key, "precision differs", mapOf("age" to 21), reader.readValue<Any>("""{"result":{"nested":[1,1,9007199254740993,0.1000000000000000002]}}"""))
        service.saveTest(key, "string differs", mapOf("age" to 21), reader.readValue<Any>("""{"result":{"nested":["1",1,9007199254740993,0.1000000000000000001]}}"""))
        val results = service.runTests(key, 1)["results"] as List<*>
        assertEquals(listOf(true, false, false), results.map { (it as Map<*, *>)["passed"] })
        assertTrue(((results.first() as Map<*, *>)["differences"] as List<*>).isEmpty())
    }
    @Test fun `audits are typed redacted and meters record success and failure`() {
        val key = "audit_${UUID.randomUUID().toString().replace("-", "")}"; val definition = service.deploy(xml(key))
        val secret = "DMN_SECRET_CANARY_123"
        val before = meters.find("easybpm.decision.evaluations").tags("policy", "UNIQUE", "status", "COMPLETED").counters().sumOf { it.count() }
        val result = service.evaluateDefinition(definition, mapOf("amount" to 5, "secret" to secret))
        assertEquals(listOf("automaticApproval"), result.matchedRuleIds)
        val row = executions.findAll().first { it.decisionKey == key && it.status == "COMPLETED" }
        assertFalse(row.inputsJson.contains(secret)); assertTrue(row.inputsJson.contains("redacted")); assertFalse(row.outputsJson.contains("APPROVED"))
        val dto = service.execution(row.id)
        assertEquals(definition.version, dto["decisionVersion"])
        assertTrue(meters.get("easybpm.decision.evaluations").tags("policy", "UNIQUE", "status", "COMPLETED").counter().count() > before)
        assertTrue(meters.get("easybpm.decision.duration").tags("policy", "UNIQUE", "status", "COMPLETED").timer().count() > 0)
        assertTrue(meters.get("easybpm.decision.matched.rules").tag("policy", "UNIQUE").counter().count() > 0)
        assertThrows(DecisionException::class.java) { service.evaluateDefinition(definition, mapOf("amount" to secret)) }
        val failed = executions.findAll().first { it.decisionKey == key && it.status == "FAILED" }
        assertEquals("TYPE_MISMATCH", failed.errorCode); assertFalse(failed.toString().contains(secret))
        assertTrue(meters.get("easybpm.decision.failures").counter().count() > 0)
        assertThrows(DecisionException::class.java) { service.executionPage(key, null, null, null, null, 0, 101) }
    }
}
