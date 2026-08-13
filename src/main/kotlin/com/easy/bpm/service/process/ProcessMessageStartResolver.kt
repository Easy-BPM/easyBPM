package com.easy.bpm.service.process

import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class ProcessMessageStartResolver(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val objectMapper: ObjectMapper
) {
    fun findMatch(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>?
    ): MessageStartMatch? =
        processDefinitionRepository.findLatestVersionProcessDefinitions()
            .asSequence()
            .mapNotNull { definition ->
                val json = BpmnXmlCodec.parseDefinition(definition.definitionJson, objectMapper)
                val startNode = findMatchingMessageStartNode(json, messageName, correlationKey, variables)
                if (startNode == null) null else MessageStartMatch(
                    definition = definition,
                    startNode = startNode,
                    variables = buildMessageStartVariables(startNode, correlationKey, variables)
                )
            }
            .firstOrNull()

    private fun findMatchingMessageStartNode(
        definition: JsonNode,
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>?
    ): JsonNode? =
        definition.get("nodes")
            .find { node ->
                NodeType.fromString(node.get("type").asText()) == NodeType.MessageStartEvent &&
                    node.get("message")?.get("name")?.asText() == messageName &&
                    matchesMessageStartCorrelation(node.get("message"), correlationKey, variables)
            }

    private fun matchesMessageStartCorrelation(
        message: JsonNode?,
        correlationKey: String,
        variables: Map<String, Any>?
    ): Boolean {
        val correlationKeys = message?.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            return true
        }

        return correlationKeys.any { keyNode ->
            val key = keyNode.asText()
            key == correlationKey || variables?.get(key)?.toString() == correlationKey
        }
    }

    private fun buildMessageStartVariables(
        messageStartNode: JsonNode,
        correlationKey: String,
        variables: Map<String, Any>?
    ): Map<String, Any> {
        val result = variables?.toMutableMap() ?: mutableMapOf()
        result.putIfAbsent("correlationKey", correlationKey)

        val payload = messageStartNode.get("message")?.get("payload")
        if (payload != null && payload.isArray) {
            payload.forEach { mapping ->
                val targetVariable = mapping.get("targetVariable")?.asText()
                    ?: mapping.get("targetName")?.asText()
                    ?: mapping.get("value")?.asText()
                    ?: return@forEach

                val sourceName = mapping.get("sourceValue")?.asText()
                    ?: mapping.get("sourceName")?.asText()
                    ?: mapping.get("value")?.asText()
                    ?: targetVariable

                if (variables?.containsKey(sourceName) == true) {
                    result[targetVariable] = variables.getValue(sourceName)
                }
            }
        }

        return result
    }
}

data class MessageStartMatch(
    val definition: ProcessDefinition,
    val startNode: JsonNode,
    val variables: Map<String, Any>
)
