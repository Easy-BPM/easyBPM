package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

import com.easy.bpm.enum.NodeType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component

@Component
class ProcessDefinitionValidator {
    fun validateAndParse(definitionJson: JsonNode): JsonNode {
        val json = definitionJson.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Root JSON must be an object")

        json.get("processId") ?: throw IllegalArgumentException("Missing 'processId'")

        val nodes = json.get("nodes")
            ?: throw IllegalArgumentException("Missing 'nodes'")
        require(nodes.isArray) { "'nodes' must be an array" }

        val flows = json.get("flows")
            ?: throw IllegalArgumentException("Missing 'flows'")
        require(flows.isArray) { "'flows' must be an array" }

        val nodeIds = mutableSetOf<String>()

        nodes.forEach { node ->
            val id = node.get("id")?.asText()
                ?: throw IllegalArgumentException("Node missing 'id'")

            val typeText = node.get("type")?.asText()
                ?: throw IllegalArgumentException("Node $id missing 'type'")

            val nodeType = try {
                NodeType.fromString(typeText)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid node type '$typeText' at '$id'")
            }

            validateMessageEvent(node, nodeType, id)
            validateMessageStartOrCatchEvent(node, nodeType, id)
            validateMessageThrowEvent(node, nodeType, id)
            validateApiTask(node, nodeType, id)
            validateAgentProcessCall(node, nodeType, id)

            if (!nodeIds.add(id)) {
                throw IllegalArgumentException("Duplicate node id '$id'")
            }
        }

        return json
    }

    private fun validateMessageEvent(node: JsonNode, nodeType: NodeType, id: String) {
        if (nodeType != NodeType.MessageEvent) return

        val properties = node.get("properties")
            ?: throw IllegalArgumentException("MessageEvent $id missing 'properties'")

        properties.get("messageName")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $id missing 'messageName' in properties")

        properties.get("correlationKey")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $id missing 'correlationKey' in properties")
    }

    private fun validateMessageStartOrCatchEvent(node: JsonNode, nodeType: NodeType, id: String) {
        if (nodeType != NodeType.MessageStartEvent && nodeType != NodeType.MessageIntermediateCatchEvent) return

        val message = node.get("message")
            ?: throw IllegalArgumentException("${nodeType.typeName} $id missing 'message' object")

        message.get("name")?.asText()
            ?: throw IllegalArgumentException("${nodeType.typeName} $id missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (nodeType == NodeType.MessageIntermediateCatchEvent && (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0)) {
            throw IllegalArgumentException("MessageIntermediateCatchEvent $id missing or empty 'message.correlationKeys'")
        }
        if (correlationKeys != null && !correlationKeys.isArray) {
            throw IllegalArgumentException("${nodeType.typeName} $id has invalid 'message.correlationKeys'")
        }

        val payload = message.get("payload")
        if (payload != null && payload.isArray) {
            payload.forEach { mapping ->
                if (nodeType == NodeType.MessageIntermediateCatchEvent) {
                    mapping.get("sourceName")?.asText()
                        ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'sourceName'")
                    mapping.get("target")?.asText()
                        ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'target'")
                    mapping.get("value")?.asText()
                        ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $id payload missing 'value'")
                } else {
                    mapping.get("targetVariable")?.asText()
                        ?: mapping.get("targetName")?.asText()
                        ?: mapping.get("value")?.asText()
                        ?: throw IllegalArgumentException("MessageStartEvent $id payload missing 'targetVariable'")
                }
            }
        }
    }

    private fun validateMessageThrowEvent(node: JsonNode, nodeType: NodeType, id: String) {
        if (nodeType != NodeType.MessageIntermediateThrowEvent) return

        val message = node.get("message")
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing 'message' object")

        message.get("name")?.asText()
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            throw IllegalArgumentException("MessageIntermediateThrowEvent $id missing or empty 'message.correlationKeys'")
        }

        val payload = message.get("payload")
        if (payload != null && payload.isArray) {
            payload.forEach { mapping ->
                mapping.get("targetName")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'targetName'")
                mapping.get("source")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'source'")
                mapping.get("value")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $id payload missing 'value'")
            }
        }
    }

    private fun validateApiTask(node: JsonNode, nodeType: NodeType, id: String) {
        if (nodeType != NodeType.APITask) return

        val properties = node.get("properties") ?: node.get("service")
            ?: throw IllegalArgumentException("APITask $id missing 'properties' or legacy 'service'")

        if (node.get("properties") == null && node.get("service") != null && node is ObjectNode) {
            node.set<JsonNode>("properties", node.get("service"))
        }

        val url = properties.get("url")?.asText()?.trim()
            ?: throw IllegalArgumentException("APITask $id missing 'url' in properties")
        if (url.isEmpty()) {
            throw IllegalArgumentException("APITask $id has empty 'url' in properties")
        }

        val auth = properties.get("auth")
        if (auth != null && !auth.isNull) {
            validateApiTaskAuth(auth, id)
        }
    }

    private fun validateApiTaskAuth(auth: JsonNode, id: String) {
        if (!auth.isObject) {
            throw IllegalArgumentException("APITask $id has invalid 'auth' format")
        }

        val authType = auth.get("type")?.asText()?.trim()?.lowercase()
            ?: throw IllegalArgumentException("APITask $id auth missing 'type'")
        if (authType !in setOf("bearer", "basic", "apikey")) {
            throw IllegalArgumentException("APITask $id auth.type '$authType' is unsupported")
        }

        val authRef = auth.get("ref")?.asText()?.trim()
            ?: throw IllegalArgumentException("APITask $id auth missing 'ref'")
        if (authRef.isEmpty()) {
            throw IllegalArgumentException("APITask $id auth.ref cannot be blank")
        }

        if (authType == "apikey") {
            val target = auth.get("in")?.asText()?.trim()?.lowercase() ?: "header"
            if (target !in setOf("header", "query")) {
                throw IllegalArgumentException("APITask $id auth.in must be 'header' or 'query'")
            }

            val keyName = auth.get("key")?.asText()?.trim() ?: "X-API-Key"
            if (keyName.isEmpty()) {
                throw IllegalArgumentException("APITask $id auth.key cannot be blank")
            }
        }
    }

    private fun validateAgentProcessCall(node: JsonNode, nodeType: NodeType, id: String) {
        if (nodeType != NodeType.AgentProcessCall) return

        val config = node.get("config")
            ?: throw IllegalArgumentException("AgentProcessCall $id missing 'config'")
        val agentProcessKey = config.get("agentProcessKey")?.asText()?.trim()
            ?: config.get("processKey")?.asText()?.trim()
            ?: throw IllegalArgumentException("AgentProcessCall $id missing 'agentProcessKey'")
        if (agentProcessKey.isEmpty()) {
            throw IllegalArgumentException("AgentProcessCall $id has empty 'agentProcessKey'")
        }
        val inputs = config.get("inputs")
        if (inputs != null && !inputs.isArray) {
            throw IllegalArgumentException("AgentProcessCall $id 'inputs' must be an array")
        }
        val outputs = config.get("outputs")
        if (outputs != null && !outputs.isArray) {
            throw IllegalArgumentException("AgentProcessCall $id 'outputs' must be an array")
        }
    }
}
