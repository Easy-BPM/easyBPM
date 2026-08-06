package com.easy.bpm.service.process

import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProcessMessageRuntimeService(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val objectMapper: ObjectMapper,
    private val navigator: ProcessNavigator,
    private val executionEngine: ProcessExecutionEngine,
    private val messageReceivedHandler: ProcessMessageReceivedHandler,
    private val messageStartResolver: ProcessMessageStartResolver,
    private val timelineService: ProcessInstanceTimelineService,
    private val instanceStarter: ProcessInstanceStarter
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessMessageRuntimeService::class.java)
    }

    @Transactional
    fun handleMessageReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ) {
        val result = messageReceivedHandler.handleReceived(messageName, correlationKey, variables)
        if (!result.subscriptionFound) {
            val startedInstance = startProcessInstanceFromMessageStart(messageName, correlationKey, variables)
                ?: throw IllegalArgumentException("No waiting subscription or message start event for message '$messageName' with correlationKey '$correlationKey'")
            logger.info("Started process instance ${startedInstance.id} from message '$messageName'")
            return
        }

        val instance = requireNotNull(result.instance)
        val definition = requireNotNull(result.definition)
        navigator.advanceProcess(instance, result.nextNodes, definition)
        executionEngine.executeNodes(result.nextNodes, instance, definition)
    }

    @Transactional
    fun handleSubscriptionTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        val errorBoundaryNode = navigator.findAttachedErrorBoundary(node, definition)
        if (errorBoundaryNode != null) {
            val nextNodes = navigator.getNextNodes(errorBoundaryNode, definition, instance)
            navigator.advanceProcess(instance, nextNodes, definition)
            executionEngine.executeNodes(nextNodes, instance, definition)
            return true
        }

        return false
    }

    @Transactional
    fun handleTimerTimeout(processInstanceId: Long, nodeId: String): Boolean {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId) ?: return false
        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)

        if (NodeType.fromString(node.get("type").asText()) != NodeType.TimerEvent) {
            return false
        }

        val nextNodes = navigator.getNextNodes(node, definition, instance)
        navigator.advanceProcess(instance, nextNodes, definition)
        executionEngine.executeNodes(nextNodes, instance, definition)
        return true
    }

    private fun startProcessInstanceFromMessageStart(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>?
    ): ProcessInstance? {
        val match = messageStartResolver.findMatch(messageName, correlationKey, variables) ?: return null
        val instance = instanceStarter.startWithDefinition(
            match.definition,
            match.variables,
            match.startNode.get("id").asText()
        )

        timelineService.record(
            processInstanceId = instance.id,
            nodeId = match.startNode.get("id").asText(),
            eventType = ProcessInstanceEventType.MESSAGE_RECEIVED,
            message = "Message '$messageName' started process with correlation key '$correlationKey'."
        )

        return instance
    }

    private fun parseDefinition(definitionJson: String): JsonNode =
        objectMapper.readTree(definitionJson)

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}
