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

import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
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
    private val timelineService: ProcessInstanceTimelineService
) {
    private val logger = LoggerFactory.getLogger(ProcessWorkerCallbackHandler::class.java)

    fun handleCompleted(
        processInstanceId: Long,
        nodeId: String,
        outputs: Map<String, String>
    ): WorkerCallbackResult {
        val startTime = System.currentTimeMillis()
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)
        timelineService.record(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.WORKER_COMPLETED,
            message = "Service task completed by worker.",
            details = outputs.takeIf { it.isNotEmpty() }?.toString()
        )

        serviceTaskOutputMapper.applyOutputMappings(instance, node, outputs)

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
    ): WorkerCallbackResult {
        val startTime = System.currentTimeMillis()
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        logger.info("handleServiceTaskFailed: instanceId=$processInstanceId, nodeId=$nodeId, errorMessage=$errorMessage")
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

    private fun parseDefinition(definitionJson: String): JsonNode =
        objectMapper.readTree(definitionJson)

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
