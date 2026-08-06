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

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.fasterxml.jackson.databind.JsonNode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class ProcessDeploymentService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processDefinitionValidator: ProcessDefinitionValidator
) {
    @Transactional
    fun deployProcess(definitionJson: JsonNode): ProcessDefinition {
        val json = processDefinitionValidator.validateAndParse(definitionJson)

        val processId = json.get("processId").asText()
        val processKey = json.get("key")?.asText()?.takeIf { it.isNotBlank() } ?: processId
        val processName = json.get("processName")?.asText()?.takeIf { it.isNotBlank() }
            ?: json.get("name")?.asText()?.takeIf { it.isNotBlank() } ?: processId
        val processDescription =
            json.get("description")?.asText()?.takeIf { it.isNotBlank() }
                ?: json.get("metadata")?.get("description")?.asText()?.takeIf { it.isNotBlank() }

        val latestVersion = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processKey)
        val nextVersion = (latestVersion?.version ?: 0) + 1

        return processDefinitionRepository.save(
            ProcessDefinition(
                key = processKey,
                processName = processName,
                description = processDescription,
                definitionJson = json.toString(),
                version = nextVersion
            )
        )
    }
}
