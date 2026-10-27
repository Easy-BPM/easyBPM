package com.easy.bpm.service.process

import com.easy.bpm.model.process.ProcessInstance
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ServiceTaskOutputMapper(
    private val objectMapper: ObjectMapper,
    private val variableManager: ProcessVariableManager
) {
    private val logger = LoggerFactory.getLogger(ServiceTaskOutputMapper::class.java)

    fun applyOutputMappings(instance: ProcessInstance, node: JsonNode, outputs: Map<String, String>) {
        val responseJson = parseResponseJson(outputs)
        val nodeConfig = node.get("properties") ?: node.get("service") ?: node.get("config")
        val outputMappings = nodeConfig?.get("outputs")

        if (outputMappings != null && outputMappings.isArray) {
            // Resolve every mapping before the first write so invalid literals cannot partially apply.
            val mapped = outputMappings.filter { it.path("target").asText("variable") == "variable" }.mapNotNull { mapping ->
                val target = mapping.get("targetVariable")?.asText()?.takeIf { it.isNotBlank() }
                    ?: mapping.get("value")?.asText()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val value = if (mapping.path("source").asText() == "static") {
                    staticValue(mapping)
                } else {
                    val source = mapping.get("sourceName")?.asText()?.takeIf { it.isNotBlank() }
                        ?: mapping.get("sourceValue")?.asText()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    variableManager.extractValueByPath(responseJson, source)
                }
                target to value
            }
            mapped.forEach { (target, value) -> variableManager.upsertProcessVariable(instance.id, target, value) }
        }
    }

    private fun parseResponseJson(outputs: Map<String, String>): JsonNode =
        if (outputs.containsKey("__response")) {
            try {
                objectMapper.readTree(outputs["__response"])
            } catch (e: Exception) {
                logger.warn("Failed to parse response JSON: ${e.message}")
                objectMapper.createObjectNode()
            }
        } else {
            objectMapper.valueToTree(outputs)
        }

    private fun staticValue(mapping: JsonNode): JsonNode {
        val raw = mapping.get("sourceValue") ?: throw IllegalArgumentException("API static output is missing its literal")
        val type = mapping.path("type").asText("string")
        if (raw.isNull) return objectMapper.nullNode()
        if (type == "string") return objectMapper.nodeFactory.textNode(if (raw.isTextual) raw.asText() else raw.toString())
        val parsed = if (raw.isTextual) {
            try { objectMapper.readTree(raw.asText()) ?: objectMapper.nullNode() }
            catch (_: Exception) { throw IllegalArgumentException("API static output has an invalid literal") }
        } else raw
        require(when (type) {
            "number" -> parsed.isNumber
            "integer" -> parsed.isNumber && parsed.decimalValue().stripTrailingZeros().scale() <= 0
            "boolean" -> parsed.isBoolean
            "json" -> true
            else -> false
        }) { "API static output literal does not match its declared type" }
        return parsed
    }
}
