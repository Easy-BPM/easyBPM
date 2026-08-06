package com.easy.bpm.service.process

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessServiceTaskHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val rabbitPublisher: RabbitPublisher,
    private val objectMapper: ObjectMapper,
    private val variableManager: ProcessVariableManager,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun handleApiTask(instance: ProcessInstance, node: JsonNode) {
        val config = node.get("properties")
            ?: node.get("service")
            ?: throw IllegalArgumentException("APITask ${node.get("id").asText()} missing properties/service")

        rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), config)
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = node.get("id").asText(),
            eventType = ProcessInstanceEventType.WORKER_REQUESTED,
            message = "API task request sent to worker."
        )

        touchInstance(instance)
    }

    fun handleServiceTaskNode(instance: ProcessInstance, node: JsonNode): ServiceTaskHandlingResult {
        val config = resolveConfig(instance, node)

        failForRequestedSimulation(config)

        if (config != null && config.has("variables")) {
            applyInternalVariables(instance, config.get("variables"))
            return ServiceTaskHandlingResult.CONTINUE
        }

        val properties = config ?: objectMapper.createObjectNode()
        try {
            rabbitPublisher.publishServiceTaskRequest(instance.id, node.get("id").asText(), properties)
            timelineService.record(
                processInstanceId = instance.id,
                nodeId = node.get("id").asText(),
                eventType = ProcessInstanceEventType.WORKER_REQUESTED,
                message = "Service task request sent to worker."
            )
        } catch (_: Exception) {
        }

        touchInstance(instance)
        return ServiceTaskHandlingResult.WAIT_FOR_WORKER
    }

    private fun resolveConfig(instance: ProcessInstance, node: JsonNode): JsonNode? {
        val config = node.get("config")
        if (config == null || !config.isObject) {
            return config
        }

        val configObj = config.deepCopy<ObjectNode>()
        configObj.fieldNames().forEachRemaining { field ->
            val valueNode = configObj.get(field)
            if (valueNode.isTextual && valueNode.asText().startsWith("\${") && valueNode.asText().endsWith("}")) {
                val varName = valueNode.asText().removePrefix("\${").removeSuffix("}")
                val variable = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
                if (variable != null) {
                    configObj.replace(field, variable.value)
                }
            }
        }
        return configObj
    }

    private fun failForRequestedSimulation(config: JsonNode?) {
        if (config == null || !config.has("shouldFail")) {
            return
        }

        val shouldFailNode = config.get("shouldFail")
        val shouldFail = when {
            shouldFailNode.isBoolean -> shouldFailNode.asBoolean(false)
            shouldFailNode.isTextual -> shouldFailNode.asText().equals("true", ignoreCase = true)
            else -> false
        }
        if (shouldFail) {
            throw RuntimeException("Simulated service task failure for error boundary test")
        }
    }

    private fun applyInternalVariables(instance: ProcessInstance, variables: JsonNode) {
        variables.forEach { varConfig ->
            val varName = varConfig.get("name")?.asText()
                ?: throw IllegalArgumentException("ServiceTask variable missing 'name' field")

            val source = varConfig.get("source")?.asText() ?: "static"
            val value: JsonNode = when (source) {
                "static" -> variableManager.parseStaticValue(varConfig.get("value"))
                "variable" -> {
                    val sourceVarName = varConfig.get("value")?.asText()
                        ?: throw IllegalArgumentException("ServiceTask variable missing source variable name")
                    val sourceVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, sourceVarName)
                        ?: throw IllegalArgumentException("Source variable '$sourceVarName' not found")
                    sourceVar.value
                }
                else -> throw IllegalArgumentException("Invalid variable source '$source'")
            }

            variableManager.upsertProcessVariable(instance.id, varName, value)
        }
    }

    private fun touchInstance(instance: ProcessInstance) {
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }
}

enum class ServiceTaskHandlingResult {
    CONTINUE,
    WAIT_FOR_WORKER
}
