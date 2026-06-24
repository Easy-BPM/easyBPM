package com.easy.bpm.service

import com.easy.bpm.model.agent.AgentProcessDefinition
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.fasterxml.jackson.databind.JsonNode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class AgentProcessService(
    private val definitionRepository: AgentProcessDefinitionRepository
) {
    @Transactional
    fun deploy(definitionJson: JsonNode): AgentProcessDefinition {
        val json = validateDefinition(definitionJson)
        val key = json.get("processKey")?.asText()?.takeIf { it.isNotBlank() }
            ?: json.get("key")?.asText()?.takeIf { it.isNotBlank() }
            ?: slugify(json.get("processName")?.asText()?.takeIf { it.isNotBlank() } ?: "agent-process")
        val name = json.get("processName")?.asText()?.takeIf { it.isNotBlank() } ?: key
        val description = json.get("description")?.asText()?.takeIf { it.isNotBlank() }
            ?: json.get("goal")?.asText()?.takeIf { it.isNotBlank() }
        val nextVersion = (definitionRepository.findTopByKeyOrderByVersionDesc(key)?.version ?: 0) + 1

        return definitionRepository.save(
            AgentProcessDefinition(
                key = key,
                processName = name,
                description = description,
                definitionJson = json.toString(),
                version = nextVersion
            )
        )
    }

    fun getLatestDefinitions(): List<AgentProcessDefinition> =
        definitionRepository.findLatestDefinitions()

    fun getLatestDefinition(key: String): AgentProcessDefinition? =
        definitionRepository.findTopByKeyOrderByVersionDesc(key)

    fun getVersions(key: String): List<AgentProcessDefinition> =
        definitionRepository.findVersionsByKey(key)

    private fun validateDefinition(definitionJson: JsonNode): JsonNode {
        require(definitionJson.isObject) { "Root JSON must be an object" }
        val resourceType = definitionJson.get("resourceType")?.asText()
        if (resourceType != null && resourceType != "AgentProcess") {
            throw IllegalArgumentException("resourceType must be 'AgentProcess'")
        }
        val goal = definitionJson.get("goal")?.asText()?.trim()
        require(!goal.isNullOrEmpty()) { "AgentProcess missing non-empty 'goal'" }
        val steps = definitionJson.get("steps")
        if (steps != null) {
            require(steps.isArray) { "'steps' must be an array" }
        }
        val tools = definitionJson.get("availableTools")
        if (tools != null) {
            require(tools.isArray) { "'availableTools' must be an array" }
        }
        val provider = definitionJson.get("provider")
        if (provider != null) {
            require(provider.isObject) { "'provider' must be an object" }
            val providerId = provider.get("providerId")?.asText()?.trim()
            require(!providerId.isNullOrEmpty()) { "provider.providerId is required when provider is configured" }
            val modelName = provider.get("modelName")?.asText()?.trim()
            require(!modelName.isNullOrEmpty()) { "provider.modelName is required when provider is configured" }
        }
        return definitionJson
    }

    private fun slugify(value: String): String =
        value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "agent-process" }
}
