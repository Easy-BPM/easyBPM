package com.easy.bpm.service

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
        val nodeConfig = node.get("properties") ?: node.get("config")
        val outputMappings = nodeConfig?.get("outputs")

        if (outputMappings != null && outputMappings.isArray) {
            outputMappings.forEach { mapping ->
                val target = mapping.get("target")?.asText()
                if (target == "variable") {
                    applyVariableMapping(instance, responseJson, mapping)
                }
            }
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

    private fun applyVariableMapping(instance: ProcessInstance, responseJson: JsonNode, mapping: JsonNode) {
        val sourceName = mapping.get("sourceName")?.asText()
        val targetVarName = mapping.get("value")?.asText()

        if (!sourceName.isNullOrBlank() && !targetVarName.isNullOrBlank()) {
            try {
                val value = variableManager.extractValueByPath(responseJson, sourceName)
                variableManager.upsertProcessVariable(instance.id, targetVarName, value)
                logger.info("Applied output mapping: $sourceName -> $targetVarName")
            } catch (e: Exception) {
                logger.warn("Failed to apply output mapping for $sourceName: ${e.message}")
            }
        }
    }
}
