package com.easy.bpm.service.process.handler

import com.easy.bpm.decision.DecisionDefinition
import com.easy.bpm.decision.DecisionException
import com.easy.bpm.decision.EvaluationResult
import com.easy.bpm.decision.DecisionService
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.process.ProcessVariableManager
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Maps all outputs before writing any variable, so a failed mapping cannot partially apply a result. */
@Component
class ProcessBusinessRuleTaskHandler(
    private val decisions: DecisionService,
    private val variables: ProcessVariableManager,
    private val mapper: ObjectMapper
) {
    fun execute(instance: ProcessInstance, node: JsonNode) {
        val started = System.nanoTime()
        val nodeId = node.path("id").asText()
        val config = node.path("config")
        val key = config.path("decisionKey").asText()
        var definition: DecisionDefinition? = null
        val inputs = linkedMapOf<String, Any?>()
        var evaluated: EvaluationResult? = null
        try {
            definition = decisions.resolveForTask(key, config.get("decisionVersion")?.takeUnless { it.isNull }?.asInt(), instance.id, nodeId)
            val processVariables = variables.getProcessVariablesAsMap(instance.id)
            config.path("inputs").forEach { mapping ->
                val name = mapping.path("targetName").asText()
                require(name.isNotBlank() && !inputs.containsKey(name)) { "INVALID_INPUT_MAPPING" }
                val value = when (mapping.path("source").asText()) {
                    "variable" -> {
                        val variableName = mapping.path("value").asText()
                        readPath(processVariables, variableName)
                    }
                    "static" -> mapper.convertValue(mapping.get("value"), Any::class.java)
                    else -> throw IllegalArgumentException("INVALID_INPUT_MAPPING")
                }
                checkType(value, mapping.path("type").asText())
                inputs[name] = value
            }
            val result = decisions.evaluateDefinition(definition, inputs, instance.id, nodeId)
            evaluated = result
            val outputs = linkedMapOf<String, Any?>()
            config.path("outputs").forEach { mapping ->
                val source = mapping.path("sourceName").asText()
                val target = mapping.path("value").asText()
                require(target.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) { "INVALID_VARIABLE_PATH" }
                require(outputs.keys.none { it == target || it.startsWith("$target.") || target.startsWith("$it.") }) { "INVALID_OUTPUT_MAPPING" }
                val value = if (source == "\$result") result.outputs else if (result.outputs is List<*>) {
                    result.outputs.map { row ->
                        require(row is Map<*, *> && row.containsKey(source)) { "MISSING_DECISION_OUTPUT" }
                        row[source].also { checkType(it, mapping.path("type").asText()) }
                    }
                } else {
                    val row = result.outputs as? Map<*, *> ?: throw IllegalArgumentException("OUTPUT_RESULT_REQUIRES_OBJECT")
                    require(row.containsKey(source)) { "MISSING_DECISION_OUTPUT" }
                    row[source]
                }
                if (source == "\$result" || result.outputs !is List<*>) checkType(value, mapping.path("type").asText())
                outputs[target] = value
            }
            val roots = linkedMapOf<String, Any?>()
            outputs.forEach { (path, value) ->
                val segments = path.split('.')
                val root = segments.first()
                roots[root] = writePath(roots.getOrDefault(root, processVariables[root]), segments.drop(1), value)
            }
            if (roots.isNotEmpty()) variables.assignProcessVariables(instance.id, roots)
            decisions.recordTaskSuccess(definition, inputs, result, instance.id, nodeId)
        } catch (ex: Exception) {
            // One runtime audit covers evaluation and mapping together; codes never contain supplied values.
            val code = (ex as? DecisionException)?.code
                ?: ex.message?.takeIf { it in setOf("INVALID_INPUT_MAPPING", "MISSING_PROCESS_VARIABLE", "INVALID_VARIABLE_PATH", "INVALID_OUTPUT_MAPPING", "MISSING_DECISION_OUTPUT", "OUTPUT_RESULT_REQUIRES_OBJECT", "MAPPING_TYPE_MISMATCH") }
                ?: "DECISION_MAPPING_OR_RESOLUTION_FAILED"
            decisions.recordFailure(definition, key, instance.id, nodeId, code, (System.nanoTime() - started) / 1_000_000, inputs, evaluated)
            throw IllegalArgumentException("Decision task '$nodeId' for '$key' failed (${definition?.version ?: "unresolved"}); inspect decision execution history")
        }
    }

    private fun writePath(current: Any?, segments: List<String>, value: Any?): Any? {
        if (segments.isEmpty()) return value
        require(current == null || current is Map<*, *>) { "INVALID_VARIABLE_PATH" }
        val updated = linkedMapOf<String, Any?>()
        (current as? Map<*, *>)?.forEach { (key, child) ->
            require(key is String) { "INVALID_VARIABLE_PATH" }
            updated[key] = child
        }
        val key = segments.first()
        updated[key] = writePath(updated[key], segments.drop(1), value)
        return updated
    }

    private fun readPath(root: Map<String, Any?>, path: String): Any? {
        require(path.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) { "INVALID_VARIABLE_PATH" }
        var value: Any? = root
        path.split('.').forEach { segment ->
            val objectValue = value as? Map<*, *> ?: throw IllegalArgumentException("MISSING_PROCESS_VARIABLE")
            require(objectValue.containsKey(segment)) { "MISSING_PROCESS_VARIABLE" }
            value = objectValue[segment]
        }
        return value
    }

    private fun checkType(value: Any?, type: String) {
        val valid = when (type) {
            "json" -> true
            "string" -> value is String
            "number" -> value is Number && value.toString().toBigDecimalOrNull() != null
            "boolean" -> value is Boolean
            "date" -> value is String && runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)
            else -> false
        }
        require(valid) { "MAPPING_TYPE_MISMATCH" }
    }
}
