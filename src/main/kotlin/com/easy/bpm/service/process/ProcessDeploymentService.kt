package com.easy.bpm.service.process

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class ProcessDeploymentService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processDefinitionValidator: ProcessDefinitionValidator,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun deployProcess(definitionXml: String): ProcessDefinition {
        val xml = definitionXml.takeIf { it.trimStart().startsWith("<") }
            ?: throw IllegalArgumentException("Process definitions must be deployed as BPMN XML")
        val json = processDefinitionValidator.validateAndParse(BpmnXmlCodec.xmlToInternalJson(xml, objectMapper))
        json.path("nodes").forEach { node ->
            node.path("config").get("sla")?.let { policy ->
                try {
                    require(com.easy.bpm.enum.NodeType.fromString(node.path("type").asText()) == com.easy.bpm.enum.NodeType.UserTask) { "SLA is supported only on User Tasks" }
                    com.easy.bpm.service.task.SlaPolicy.validate(policy)
                } catch (ex: IllegalArgumentException) {
                    throw IllegalArgumentException("Node ${node.path("id").asText()} config.sla: ${ex.message}")
                }
            }
        }

        val processId = json.get("processId").asText()
        val processKey = json.get("key")?.asText()?.takeIf { it.isNotBlank() }
            ?: json.get("metadata")?.get("key")?.asText()?.takeIf { it.isNotBlank() }
            ?: processId
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
                definitionJson = xml,
                version = nextVersion
            )
        )
    }
}
