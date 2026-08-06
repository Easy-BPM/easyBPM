package com.easy.bpm.service.process

import com.easy.bpm.service.*

import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProcessExecutionEngine(
    private val metricsService: MetricsService,
    private val failureHandler: ProcessFailureHandler,
    private val navigator: ProcessNavigator,
    private val messageNodeHandler: ProcessMessageNodeHandler,
    private val variableManager: ProcessVariableManager,
    private val userTaskHandler: ProcessUserTaskHandler,
    private val serviceTaskHandler: ProcessServiceTaskHandler,
    private val processAiTaskHandler: ProcessAiTaskHandler,
    private val processAgentCallHandler: ProcessAgentCallHandler,
    private val callActivityHandler: CallActivityHandler,
    private val timelineService: ProcessInstanceTimelineService,
    private val lifecycleManager: ProcessInstanceLifecycleManager
) {
    companion object {
        private const val INTERNAL_TIMER_MESSAGE_NAME = "__internal.timer__"
        private val logger = LoggerFactory.getLogger(ProcessExecutionEngine::class.java)
    }

    fun executeNodes(
        nodeIds: List<String>,
        instance: ProcessInstance,
        definition: JsonNode
    ) {
        for (nodeId in nodeIds) {
            val node = findNode(definition, nodeId)
            val handled = executeNode(instance, node, definition)
            if (handled) {
                return
            }
        }
    }

    private fun executeNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val nodeType = NodeType.fromString(node.get("type").asText())

        try {
            when (nodeType) {
                NodeType.UserTask -> userTaskHandler.handleUserTask(instance, node)
                NodeType.APITask -> serviceTaskHandler.handleApiTask(instance, node)
                NodeType.AiTask -> processAiTaskHandler.handleAiTask(instance, node)
                NodeType.AgentProcessCall -> handleAgentProcessCall(instance, node, definition)
                NodeType.ServiceTask -> handleServiceTaskNode(instance, node, definition)
                NodeType.TimerEvent -> messageNodeHandler.handleTimerEvent(instance, node, INTERNAL_TIMER_MESSAGE_NAME)
                NodeType.MessageEvent -> messageNodeHandler.handleMessageEvent(instance, node)
                NodeType.MessageStartEvent -> continueFromNode(instance, node, definition)
                NodeType.MessageIntermediateCatchEvent -> messageNodeHandler.handleMessageIntermediateCatchEvent(instance, node)
                NodeType.MessageIntermediateThrowEvent -> handleMessageIntermediateThrowEvent(instance, node, definition)
                NodeType.CallActivity -> handleCallActivity(instance, node, definition)
                NodeType.EndEvent -> lifecycleManager.finishProcess(instance)
                NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> handleGateway(
                    instance,
                    node,
                    definition
                )
                else -> { /* no-op for other node types */ }
            }
        } catch (ex: Exception) {
            return handleNodeFailure(instance, node, definition, nodeType, ex, startTime)
        }

        recordNodeExecution(startTime, nodeType)
        return false
    }

    private fun handleGateway(instance: ProcessInstance, node: JsonNode, definition: JsonNode) {
        val nextNodes = navigator.getNextNodes(node, definition, instance)
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = node.get("id").asText(),
            eventType = ProcessInstanceEventType.GATEWAY_EVALUATED,
            message = "Gateway routed to ${nextNodes.joinToString(", ")}."
        )
        navigator.advanceProcess(instance, nextNodes, definition)
        executeNodes(nextNodes, instance, definition)
    }

    private fun handleAgentProcessCall(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        val nextNodes = processAgentCallHandler.handleAgentProcessCall(instance, node, definition)
        navigator.advanceProcess(instance, nextNodes, definition)
        executeNodes(nextNodes, instance, definition)
    }

    private fun handleServiceTaskNode(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        if (serviceTaskHandler.handleServiceTaskNode(instance, node) == ServiceTaskHandlingResult.CONTINUE) {
            continueFromNode(instance, node, definition)
        }
    }

    private fun handleMessageIntermediateThrowEvent(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        messageNodeHandler.publishMessageIntermediateThrowEvent(instance, node)
        continueFromNode(instance, node, definition)
    }

    private fun handleCallActivity(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ) {
        try {
            callActivityHandler.executeCallActivity(instance, node, definition)
            logger.info("Call activity node '${node.get("id").asText()}' executed successfully")
        } catch (ex: Exception) {
            logger.error("Error executing call activity node", ex)
            throw ex
        }
    }

    private fun handleNodeFailure(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode,
        nodeType: NodeType,
        ex: Exception,
        startTime: Long
    ): Boolean {
        val errorBoundaryNode = navigator.findAttachedErrorBoundary(node, definition)
        if (errorBoundaryNode != null) {
            val errorMessage = ex.message ?: ex.javaClass.simpleName
            val errorCode = errorBoundaryNode.get("config")?.get("errorCode")?.asText() ?: "ERROR"
            val exceptionVariableName = errorBoundaryNode.get("config")?.get("exceptionVariable")?.asText()

            logger.warn("Error caught by boundary [$errorCode] on node ${node.get("id").asText()}: $errorMessage", ex)

            if (!exceptionVariableName.isNullOrBlank()) {
                variableManager.assignProcessVariables(instance.id, mapOf(exceptionVariableName to errorMessage))
                logger.debug("Error message captured to variable '$exceptionVariableName'")
            }

            val nextNodes = navigator.getNextNodes(errorBoundaryNode, definition, instance)
            navigator.advanceProcess(instance, nextNodes, definition)
            executeNodes(nextNodes, instance, definition)
            recordNodeExecution(startTime, nodeType)
            return true
        }

        val nodeId = node.get("id").asText()
        val errorMessage = ex.message ?: ex.javaClass.simpleName
        logger.error("Unhandled process node failure: instance=${instance.id}, nodeId=$nodeId", ex)
        failureHandler.failInstance(
            instance = instance,
            nodeId = nodeId,
            errorMessage = errorMessage,
            incidentSource = failureHandler.incidentSourceForNode(nodeType),
            createIncident = nodeType != NodeType.CodeTask && nodeType != NodeType.AiTask
        )
        recordNodeExecution(startTime, nodeType)
        return true
    }

    private fun continueFromNode(instance: ProcessInstance, node: JsonNode, definition: JsonNode) {
        val nextNodes = navigator.getNextNodes(node, definition, instance)
        navigator.advanceProcess(instance, nextNodes, definition)
        executeNodes(nextNodes, instance, definition)
    }

    private fun recordNodeExecution(startTime: Long, nodeType: NodeType) {
        val duration = System.currentTimeMillis() - startTime
        metricsService.recordNodeExecution(duration, nodeType.toString())
    }

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}
