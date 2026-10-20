package com.easy.bpm.decision

import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class DecisionService(
    private val definitions: DecisionDefinitionRepository,
    private val pins: DecisionTaskPinRepository,
    private val cases: DecisionTestCaseRepository,
    private val executions: DecisionExecutionRepository,
    private val instances: ProcessInstanceRepository,
    private val evaluator: DecisionEvaluator,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val meters: MeterRegistry
) {
    fun validate(xml: String) = evaluator.validate(xml)
    fun test(xml: String, inputs: Map<String, Any?>): EvaluationResult = evaluator.evaluate(evaluator.requireValid(xml), bounded(inputs))
    @Transactional
    fun deploy(xml: String): DecisionDefinition {
        val table = evaluator.requireValid(xml)
        // Transaction-scoped PostgreSQL lock also protects first deployment of a previously absent key.
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", { rs -> rs.getObject(1); Unit }, "easybpm.dmn.${table.key}")
        val version = (definitions.findTopByKeyOrderByVersionDesc(table.key)?.version ?: 0) + 1
        return definitions.saveAndFlush(DecisionDefinition(key = table.key, name = table.name, version = version, dmnXml = xml))
    }
    fun latest() = definitions.latest()
    fun versions(key: String): List<DecisionDefinition> = definitions.findByKeyOrderByVersionDesc(key).also { if (it.isEmpty()) missing() }
    fun resolve(key: String, version: Int?): DecisionDefinition {
        if (version != null && version <= 0) throw DecisionException("INVALID_VERSION", "Decision version must be positive")
        return (if (version == null) definitions.findTopByKeyOrderByVersionDesc(key) else definitions.findByKeyAndVersion(key, version)) ?: missing()
    }
    @Transactional(noRollbackFor = [DecisionException::class])
    fun resolveForTask(key: String, version: Int?, instanceId: Long, nodeId: String): DecisionDefinition {
        instances.findByIdForUpdate(instanceId) ?: throw DecisionException("INSTANCE_NOT_FOUND", "Process instance not found", 404)
        pins.findByInstanceIdAndNodeId(instanceId, nodeId)?.let { pin ->
            val definition = definitions.findById(pin.definitionId).orElseThrow { DecisionException("DECISION_NOT_FOUND", "Pinned decision not found", 404) }
            if (definition.key != key || (version != null && definition.version != version)) throw DecisionException("PIN_CONFLICT", "Task decision reference conflicts with its immutable binding", 409)
            return definition
        }
        val definition = resolve(key, version)
        pins.saveAndFlush(DecisionTaskPin(instanceId = instanceId, nodeId = nodeId, definitionId = definition.id))
        return definition
    }
    // No transactional proxy here: the process engine catches domain failures and persists incidents.
    // Throwing through a REQUIRED proxy would incorrectly mark that outer transaction rollback-only.
    fun evaluateDefinition(definition: DecisionDefinition, inputs: Map<String, Any?>, processInstanceId: Long? = null, nodeId: String? = null): EvaluationResult {
        val started = System.nanoTime()
        try {
            val result = evaluator.evaluate(evaluator.requireValid(definition.dmnXml), bounded(inputs), definition.version)
            if (processInstanceId == null) recordSuccess(definition, inputs, result, null, nodeId)
            return result
        } catch (e: DecisionException) {
            // Runtime handler records a single failure, including output-mapping failures.
            if (processInstanceId == null) {
                executions.save(DecisionExecution(decisionKey = definition.key, decisionVersion = definition.version, status = "FAILED", errorCode = e.code, inputsJson = json(redact(inputs)), durationMs = (System.nanoTime() - started) / 1_000_000))
                metric("UNKNOWN", "FAILED", (System.nanoTime() - started) / 1_000_000)
            }
            throw e
        }
    }
    fun recordTaskSuccess(definition: DecisionDefinition, inputs: Map<String, Any?>, result: EvaluationResult, instanceId: Long, nodeId: String) = recordSuccess(definition, inputs, result, instanceId, nodeId)
    private fun recordSuccess(definition: DecisionDefinition, inputs: Map<String, Any?>, result: EvaluationResult, instanceId: Long?, nodeId: String?) {
        executions.save(DecisionExecution(decisionKey = definition.key, decisionVersion = definition.version, processInstanceId = instanceId, nodeId = nodeId,
            durationMs = result.durationMs, matchedRuleIdsJson = json(result.matchedRuleIds), inputsJson = json(redact(inputs)), outputsJson = json(redact(result.outputs)), explanationsJson = json(result.explanations)))
        metric(result.hitPolicy, "COMPLETED", result.durationMs)
        result.matchedRuleIds.forEach { meters.counter("easybpm.decision.matched.rules", "policy", result.hitPolicy).increment() }
    }
    fun recordFailure(definition: DecisionDefinition?, key: String, instanceId: Long, nodeId: String, errorCode: String, durationMs: Long = 0, inputs: Map<String, Any?> = emptyMap(), result: EvaluationResult? = null) {
        executions.save(DecisionExecution(decisionKey = key.take(128), decisionVersion = definition?.version, processInstanceId = instanceId, nodeId = nodeId, status = "FAILED", durationMs = durationMs, inputsJson = json(redact(inputs)), outputsJson = json(redact(result?.outputs)), matchedRuleIdsJson = json(result?.matchedRuleIds ?: emptyList<String>()), explanationsJson = json(result?.explanations ?: emptyList<RuleExplanation>()), errorCode = errorCode.takeIf { Regex("[A-Z_]{1,100}").matches(it) } ?: "DECISION_TASK_FAILED"))
        metric("UNKNOWN", "FAILED", durationMs)
    }
    fun savedTests(key: String): List<Map<String, Any?>> { resolve(key, null); return cases.findByDecisionKeyOrderById(key).map(::testDto) }
    @Transactional
    fun saveTest(key: String, name: String, inputs: Map<String, Any?>, expectedOutputs: Any?): Map<String, Any?> {
        resolve(key, null)
        if (name.isBlank() || name.length > 255) throw DecisionException("INVALID_NAME", "Test name is required and limited to 255 characters")
        bounded(inputs)
        if (json(expectedOutputs).toByteArray().size > 1_000_000) throw DecisionException("LIMIT", "Expected result exceeds 1 MB")
        return testDto(cases.save(DecisionTestCase(decisionKey = key, name = name, inputsJson = json(inputs), expectedOutputsJson = json(expectedOutputs))))
    }
    fun runTests(key: String, version: Int?): Map<String, Any?> {
        val definition = resolve(key, version)
        val table = evaluator.requireValid(definition.dmnXml)
        val results = cases.findByDecisionKeyOrderById(key).map { case ->
            val expected = mapper.readTree(case.expectedOutputsJson)
            var actual: Any? = null; var error: String? = null
            try { actual = evaluator.evaluate(table, readMap(case.inputsJson), definition.version).outputs } catch (e: DecisionException) { error = e.code }
            val actualTree = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(actual)
            val passed = error == null && expected == actualTree
            mapOf("testCaseId" to case.id, "name" to case.name, "passed" to passed, "expectedOutputs" to expected, "actualOutputs" to actual, "error" to error,
                "differences" to if (passed) emptyList<Any>() else listOf(mapOf("path" to "outputs", "expected" to expected, "actual" to actual)))
        }
        return mapOf("decisionKey" to key, "version" to definition.version, "results" to results)
    }
    fun diff(key: String, fromVersion: Int, toVersion: Int): Map<String, Any?> {
        val before = evaluator.requireValid(resolve(key, fromVersion).dmnXml); val after = evaluator.requireValid(resolve(key, toVersion).dmnXml)
        val a = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(before); val b = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(after)
        val changes = a.fieldNames().asSequence().filter { a[it] != b[it] }.map { mapOf("path" to it, "before" to a[it], "after" to b[it]) }.toList()
        return mapOf("decisionKey" to key, "fromVersion" to fromVersion, "toVersion" to toVersion, "changes" to changes)
    }
    fun executionPage(key: String?, version: Int?, instanceId: Long?, nodeId: String?, status: String?, page: Int, size: Int): Map<String, Any> {
        if (page < 0 || size !in 1..100 || (version != null && version <= 0)) throw DecisionException("INVALID_PAGE", "Page must be nonnegative and size 1–100")
        if (status != null && status !in setOf("COMPLETED", "FAILED")) throw DecisionException("INVALID_STATUS", "Status must be COMPLETED or FAILED")
        val spec = Specification<DecisionExecution> { root, _, cb -> cb.and(*listOfNotNull(
            key?.let { cb.equal(root.get<String>("decisionKey"), it) }, version?.let { cb.equal(root.get<Int>("decisionVersion"), it) },
            instanceId?.let { cb.equal(root.get<Long>("processInstanceId"), it) }, nodeId?.let { cb.equal(root.get<String>("nodeId"), it) }, status?.let { cb.equal(root.get<String>("status"), it) }).toTypedArray()) }
        val result = executions.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "executedAt", "id")))
        return mapOf("content" to result.content.map(::executionDto), "totalElements" to result.totalElements, "totalPages" to result.totalPages, "number" to page, "size" to size)
    }
    fun execution(id: Long) = executionDto(executions.findById(id).orElseThrow { DecisionException("EXECUTION_NOT_FOUND", "Execution not found", 404) })
    private fun executionDto(e: DecisionExecution): Map<String, Any?> = mapOf("id" to e.id, "decisionKey" to e.decisionKey, "decisionVersion" to e.decisionVersion, "processInstanceId" to e.processInstanceId, "nodeId" to e.nodeId, "status" to e.status, "durationMs" to e.durationMs, "matchedRuleIds" to mapper.readTree(e.matchedRuleIdsJson), "inputs" to mapper.readTree(e.inputsJson), "outputs" to mapper.readTree(e.outputsJson), "explanations" to mapper.readTree(e.explanationsJson), "errorCode" to e.errorCode, "executedAt" to e.executedAt)
    private fun testDto(t: DecisionTestCase): Map<String, Any?> = mapOf("id" to t.id, "name" to t.name, "inputs" to mapper.readTree(t.inputsJson), "expectedOutputs" to mapper.readTree(t.expectedOutputsJson))
    private fun metric(policy: String, status: String, duration: Long) {
        meters.counter("easybpm.decision.evaluations", "policy", policy, "status", status).increment()
        meters.timer("easybpm.decision.duration", "policy", policy, "status", status).record(duration, TimeUnit.MILLISECONDS)
        if (status == "FAILED") meters.counter("easybpm.decision.failures").increment()
    }
    private fun bounded(inputs: Map<String, Any?>): Map<String, Any?> { if (json(inputs).toByteArray().size > 1_000_000) throw DecisionException("LIMIT", "Inputs exceed 1 MB"); return inputs }
    private fun redact(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { it.key.toString() to redact(it.value) }
        is List<*> -> mapOf("type" to "array", "size" to value.size, "redacted" to true)
        null -> mapOf("type" to "null", "redacted" to true)
        else -> mapOf("type" to when (value) { is Boolean -> "boolean"; is Number -> "number"; else -> "string" }, "redacted" to true)
    }
    private fun json(value: Any?) = mapper.writeValueAsString(value)
    @Suppress("UNCHECKED_CAST") private fun readMap(text: String) = mapper.readValue(text, Map::class.java) as Map<String, Any?>
    private fun missing(): Nothing = throw DecisionException("DECISION_NOT_FOUND", "Decision key or version not found", 404)
}
