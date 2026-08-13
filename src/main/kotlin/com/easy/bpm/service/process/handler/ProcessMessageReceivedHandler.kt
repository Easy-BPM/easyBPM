package com.easy.bpm.service.process.handler

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.process.ProcessNavigator
import com.easy.bpm.service.process.ProcessVariableManager
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class ProcessMessageReceivedHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val messageSubscriptionService: MessageSubscriptionService,
    private val objectMapper: ObjectMapper,
    private val variableManager: ProcessVariableManager,
    private val navigator: ProcessNavigator,
    private val metricsService: MetricsService,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun handleReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ): MessageReceivedResult {
        metricsService.recordMessageEventReceived(messageName)

        val subscription = messageSubscriptionService.receiveMessage(messageName, correlationKey, variables)
            ?: return MessageReceivedResult(subscriptionFound = false)

        val instance = processInstanceRepository.findByIdForUpdate(subscription.processInstanceId)
            ?: throw IllegalArgumentException("Process instance ${subscription.processInstanceId} not found")

        variableManager.saveMessageVariables(instance, variables)
        messageSubscriptionService.deleteSubscription(subscription.id)
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = subscription.nodeId,
            eventType = ProcessInstanceEventType.MESSAGE_RECEIVED,
            message = "Message '$messageName' received with correlation key '$correlationKey'."
        )

        val definition = BpmnXmlCodec.parseDefinition(instance.processDefinition.definitionJson, objectMapper)
        val node = findNode(definition, subscription.nodeId)

        return MessageReceivedResult(
            subscriptionFound = true,
            instance = instance,
            definition = definition,
            nextNodes = navigator.getNextNodes(node, definition, instance)
        )
    }

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}

data class MessageReceivedResult(
    val subscriptionFound: Boolean,
    val instance: ProcessInstance? = null,
    val definition: JsonNode? = null,
    val nextNodes: List<String> = emptyList()
)
