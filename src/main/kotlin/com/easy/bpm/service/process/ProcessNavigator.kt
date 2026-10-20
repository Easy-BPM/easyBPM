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
        definition: JsonNode,
        completedNodeId: String? = null
    ) {
        val waiting = nextNodes.isEmpty() && completedNodeId != null &&
            NodeType.fromString(findNode(definition, completedNodeId).get("type").asText()) == NodeType.ParallelGateway
        ProcessTokenState.advance(instance, completedNodeId, nextNodes, waiting)

        // History must never evaluate a gateway: join evaluation consumes an arrival.
        nextNodes.filter { id ->
            NodeType.fromString(findNode(definition, id).get("type").asText()) !in
                setOf(NodeType.ExclusiveGateway, NodeType.ParallelGateway, NodeType.InclusiveGateway)
        }.forEach { id ->
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

        val isCompleted = instance.currentNode.orEmpty().isEmpty() && !waiting

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
