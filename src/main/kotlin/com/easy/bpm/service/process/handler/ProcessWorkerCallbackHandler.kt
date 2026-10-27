package com.easy.bpm.service.process.handler

import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.process.ProcessNavigator
import com.easy.bpm.service.process.ProcessVariableManager
import com.easy.bpm.service.process.ServiceTaskOutputMapper
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProcessWorkerCallbackHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val objectMapper: ObjectMapper,
    private val navigator: ProcessNavigator,
    private val serviceTaskOutputMapper: ServiceTaskOutputMapper,
    private val failureHandler: ProcessFailureHandler,
    private val timelineService: ProcessInstanceTimelineService,
    private val variableManager: ProcessVariableManager
) {
    private val logger = LoggerFactory.getLogger(ProcessWorkerCallbackHandler::class.java)

    fun handleCompleted(
        processInstanceId: Long,
        nodeId: String,
        outputs: Map<String, String>
    ): WorkerCallbackResult? {
        val startTime = System.currentTimeMillis()
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        if (!acceptsCallback(instance, nodeId)) return null
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)
        try {
            serviceTaskOutputMapper.applyOutputMappings(instance, node, outputs)
        } catch (_: IllegalArgumentException) {
            return handleFailed(processInstanceId, nodeId, "API output mapping configuration is invalid")
        }
        timelineService.record(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.WORKER_COMPLETED,
            message = "Service task completed by worker.",
            details = outputs.takeIf { it.isNotEmpty() }?.toString()
        )

        return WorkerCallbackResult(
            instance = instance,
            definition = definition,
            nextNodes = navigator.getNextNodes(node, definition, instance),
            startTime = startTime,
            success = true
        )
    }

    fun handleFailed(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String?,
        incidentSource: IncidentSource = IncidentSource.WORKER,
        externalReferenceId: String? = null
    ): WorkerCallbackResult? {
        val startTime = System.currentTimeMillis()
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        if (!acceptsCallback(instance, nodeId)) return null
        logger.info("API task failure: instanceId={}, nodeId={}", processInstanceId, nodeId)
        timelineService.record(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.WORKER_FAILED,
            message = errorMessage ?: "Service task failed."
        )

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)
        val errorBoundaryNode = navigator.findAttachedErrorBoundary(node, definition)

        if (errorBoundaryNode != null) {
            captureBoundaryError(instance, errorBoundaryNode, errorMessage)
            logger.info("Found error boundary for node $nodeId, advancing to boundary node")
            return WorkerCallbackResult(
                instance = instance,
                definition = definition,
                nextNodes = navigator.getNextNodes(errorBoundaryNode, definition, instance),
                startTime = startTime,
                success = false
            )
        }

        logger.info("No error boundary found for node $nodeId, marking instance $processInstanceId as FAILED")
        failureHandler.failInstance(
            instance = instance,
            nodeId = nodeId,
            errorMessage = errorMessage ?: "Service task failed",
            incidentSource = incidentSource,
            externalReferenceId = externalReferenceId
        )
        logger.info("Instance $processInstanceId status set to FAILED")

        return WorkerCallbackResult(
            instance = instance,
            definition = definition,
            nextNodes = emptyList(),
            startTime = startTime,
            success = false,
            shouldContinue = false
        )
    }

    private fun captureBoundaryError(instance: ProcessInstance, boundary: JsonNode, reason: String?) {
        val name = boundary.path("config").path("exceptionVariable").asText().trim()
        if (name.isEmpty()) return
        // Never copy response bodies, credentials or request URLs into process variables.
        val httpStatus = Regex("\\b([45]\\d{2})\\b").find(reason.orEmpty())?.groupValues?.get(1)
        val message = when {
            httpStatus != null -> "API request failed with HTTP $httpStatus."
            reason.orEmpty().contains("timed out", true) || reason.orEmpty().contains("timeout", true) -> "API request timed out."
            reason.orEmpty().contains("environment variable", true) -> "API authentication configuration is unavailable."
            reason.orEmpty().contains("connect", true) || reason.orEmpty().contains("UnknownHost", true) -> "API request could not connect to the provider."
            else -> "API task failed. Review worker diagnostics."
        }
        variableManager.assignProcessVariables(instance.id, mapOf(name to message))
    }

    // The caller holds the process lock through continuation. Consuming the active token
    // makes duplicate/late callbacks no-ops before outputs, events or recovery tasks change.
    private fun acceptsCallback(instance: ProcessInstance, nodeId: String): Boolean =
        instance.status in setOf(ProcessStatus.ACTIVE, ProcessStatus.WAITING) &&
            nodeId in instance.currentNode.orEmpty()

    private fun parseDefinition(definitionJson: String): JsonNode =
        BpmnXmlCodec.parseDefinition(definitionJson, objectMapper)

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}

data class WorkerCallbackResult(
    val instance: ProcessInstance,
    val definition: JsonNode,
    val nextNodes: List<String>,
    val startTime: Long,
    val success: Boolean,
    val shouldContinue: Boolean = true
)
