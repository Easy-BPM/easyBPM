package com.easy.bpm.service.process

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

/** Resolve request data in the backend, where committed process variables are authoritative. */
internal object ApiTaskRequestResolver {
    private val token = Regex("\\{\\{([^{}]+)}}|\\$\\{([^{}]+)}")

    fun resolve(config: JsonNode, variables: Map<String, JsonNode>): JsonNode {
        require(config.isObject) { "API task configuration must be an object" }
        val resolved = config.deepCopy<ObjectNode>()
        config.get("url")?.takeIf { it.isTextual }?.let {
            resolved.put("url", interpolate(it.asText(), variables, encodeUri = true))
        }
        config.get("body")?.let { resolved.set<JsonNode>("body", resolveValue(it, variables)) }
        config.get("headers")?.takeIf { it.isObject }?.let { headers ->
            val copy = headers.deepCopy<ObjectNode>()
            headers.fields().forEachRemaining { (name, value) ->
                copy.put(name, interpolate(if (value.isTextual) value.asText() else value.toString(), variables))
            }
            resolved.set<JsonNode>("headers", copy)
        }
        // Auth references and output definitions are configuration, not request data templates.
        return resolved
    }

    private fun resolveValue(node: JsonNode, variables: Map<String, JsonNode>): JsonNode = when {
        node.isObject -> node.deepCopy<ObjectNode>().also { copy ->
            node.fields().forEachRemaining { (key, value) -> copy.set<JsonNode>(key, resolveValue(value, variables)) }
        }
        node.isArray -> node.deepCopy<ArrayNode>().also { copy ->
            node.forEachIndexed { index, value -> copy.set(index, resolveValue(value, variables)) }
        }
        node.isTextual -> {
            val text = node.asText()
            val whole = token.matchEntire(text)
            if (whole != null) value(whole, variables).deepCopy<JsonNode>()
            else JsonNodeFactory.instance.textNode(interpolate(text, variables))
        }
        else -> node.deepCopy<JsonNode>()
    }

    private fun value(match: MatchResult, variables: Map<String, JsonNode>): JsonNode {
        val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return variables[name] ?: throw IllegalArgumentException("API request template references a missing process variable")
    }

    private fun interpolate(text: String, variables: Map<String, JsonNode>, encodeUri: Boolean = false): String =
        token.replace(text) { match ->
            val node = value(match, variables)
            val replacement = if (node.isTextual) node.asText() else node.toString()
            if (encodeUri) UriUtils.encode(replacement, StandardCharsets.UTF_8) else replacement
        }
}

