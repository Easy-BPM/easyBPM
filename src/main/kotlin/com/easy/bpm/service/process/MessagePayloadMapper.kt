package com.easy.bpm.service.process

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Resolve and validate the whole mapping before any process variable is written. */
internal object MessagePayloadMapper {
    fun resolve(node: JsonNode, definition: JsonNode, incoming: Map<String, Any>?, mapper: ObjectMapper): Map<String, JsonNode>? {
        val mappings = node.path("message").get("payload") ?: return null
        if (mappings.isArray && mappings.isEmpty) return null
        if (!mappings.isArray) throw MessagePayloadMappingException("Message payload mappings must be an array.")
        val declared = definition.path("variables").map { it.path("name").asText() }.toSet()
        val payload = mapper.valueToTree<JsonNode>(incoming ?: emptyMap<String, Any>())
        val result = linkedMapOf<String, JsonNode>()
        mappings.forEachIndexed { index, mapping ->
            fun fail(reason: String): Nothing = throw MessagePayloadMappingException("Message payload mapping ${index + 1}: $reason")
            val target = mapping.get("targetVariable")?.asText() ?: mapping.get("value")?.asText()
            if (target.isNullOrBlank() || target !in declared || mapping.path("target").asText("variable") != "variable") fail("target must reference a declared process variable.")
            if (result.containsKey(target)) fail("duplicate target variable.")
            val type = mapping.path("type").asText("json")
            val value = when (mapping.path("source").asText()) {
                "variable" -> {
                    val source = mapping.get("sourceValue")?.asText() ?: mapping.get("sourceName")?.asText()
                    if (source.isNullOrBlank() || !payload.has(source)) fail("source is missing from the received payload.")
                    payload.get(source)
                }
                "static" -> {
                    val literal = mapping.get("sourceValue") ?: mapping.get("sourceName") ?: fail("static source is missing.")
                    if (type != "string" && literal.isTextual) {
                        try { mapper.readTree(literal.asText()) ?: fail("invalid static value.") }
                        catch (_: com.fasterxml.jackson.core.JsonProcessingException) { fail("invalid static value.") }
                    } else literal
                }
                else -> fail("unsupported source kind.")
            }
            val validType = when (type) {
                "json" -> true
                "string" -> value.isNull || value.isTextual
                "number" -> value.isNull || value.isNumber
                "boolean" -> value.isNull || value.isBoolean
                else -> false
            }
            if (!validType) fail("value does not match the configured type.")
            result[target] = value
        }
        return result
    }
}

class MessagePayloadMappingException(message: String) : IllegalStateException(message)
