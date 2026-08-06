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

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
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

        val definition = objectMapper.readTree(instance.processDefinition.definitionJson)
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
