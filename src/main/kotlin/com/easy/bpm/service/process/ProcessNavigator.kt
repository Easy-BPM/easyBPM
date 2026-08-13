package com.easy.bpm.service.process

import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.variable.HistoricVariableArchiver
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessNavigator(
    private val gatewayService: GatewayService,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val timelineService: ProcessInstanceTimelineService,
    private val historicVariableArchiver: HistoricVariableArchiver
) {
    fun getNextNodes(node: JsonNode, definition: JsonNode, instance: ProcessInstance): List<String> =
        gatewayService.getNextNodes(node, definition, instance)

    fun getStartNodes(instance: ProcessInstance, definition: JsonNode, startNodeId: String? = null): List<String> {
        val start = if (startNodeId != null) {
            findNode(definition, startNodeId).also {
                if (NodeType.fromString(it.get("type").asText()) !in setOf(NodeType.StartEvent, NodeType.MessageStartEvent)) {
                    throw IllegalArgumentException("Node '$startNodeId' is not a start event")
                }
            }
        } else {
            definition.get("nodes")
                .find { NodeType.fromString(it.get("type").asText()) == NodeType.StartEvent }
                ?: throw IllegalArgumentException("StartEvent not found")
        }

        return getNextNodes(start, definition, instance).also {
            if (it.isEmpty()) {
                throw IllegalArgumentException("${NodeType.fromString(start.get("type").asText()).typeName} has no outgoing flow")
            }
        }
    }

    fun findAttachedErrorBoundary(node: JsonNode, definition: JsonNode): JsonNode? {
        val nodeId = node.get("id").asText()
        val nodes = definition.get("nodes")
        return nodes.firstOrNull {
            NodeType.fromString(it.get("type").asText()) == NodeType.ErrorBoundaryEvent &&
                it.has("attachedTo") && it.get("attachedTo").asText() == nodeId
        }
    }

    fun advanceProcess(
        instance: ProcessInstance,
        nextNodes: List<String>,
        definition: JsonNode
    ) {
        instance.currentNode = nextNodes

        val appendable = mutableListOf<String>()

        fun resolveAndCollect(id: String) {
            val node = definition.get("nodes").find { it.get("id").asText() == id } ?: return
            when (NodeType.fromString(node.get("type").asText())) {
                NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway -> {
                    val targets = getNextNodes(node, definition, instance)
                    targets.forEach { resolveAndCollect(it) }
                }
                NodeType.UserTask,
                NodeType.CodeTask,
                NodeType.ServiceTask,
                NodeType.AgentProcessCall,
                NodeType.APITask,
                NodeType.MessageEvent,
                NodeType.MessageIntermediateCatchEvent,
                NodeType.MessageIntermediateThrowEvent,
                NodeType.TimerEvent,
                NodeType.ScriptTask,
                NodeType.EndEvent -> appendable.add(id)
                else -> { /* ignore other node types */ }
            }
        }

        nextNodes.forEach { resolveAndCollect(it) }

        appendable.forEach { id ->
            if (instance.nodeHistory.lastOrNull() != id) {
                instance.nodeHistory = instance.nodeHistory + id
                timelineService.record(
                    processInstanceId = instance.id,
                    nodeId = id,
                    eventType = ProcessInstanceEventType.NODE_ENTERED,
                    message = "Entered node '$id'."
                )
            }
        }
        instance.updatedAt = LocalDateTime.now()

        val isCompleted = nextNodes.isEmpty() || nextNodes.all { nodeId ->
            val node = definition.get("nodes").find { it.get("id").asText() == nodeId }
            node != null && NodeType.fromString(node.get("type").asText()) == NodeType.EndEvent
        }

        if (isCompleted) {
            instance.status = ProcessStatus.COMPLETED
            instance.currentNode = emptyList()
            timelineService.record(
                processInstanceId = instance.id,
                eventType = ProcessInstanceEventType.PROCESS_COMPLETED,
                message = "Process instance completed."
            )
        }

        processInstanceRepository.save(instance)
        if (isCompleted) {
            historicVariableArchiver.archiveProcessInstanceVariables(instance.id)
        }
    }

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}
