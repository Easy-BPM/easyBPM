package com.easy.bpm.service.process.handler

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.process.ProcessVariableManager
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessMessageNodeHandler(
    private val messageSubscriptionService: MessageSubscriptionService,
    private val rabbitPublisher: RabbitPublisher,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val variableManager: ProcessVariableManager,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun handleMessageEvent(instance: ProcessInstance, node: JsonNode) {
        val nodeId = node.get("id").asText()
        val properties = node.get("properties")
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing properties")

        val messageName = properties.get("messageName")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing messageName")

        val correlationKeyTemplate = properties.get("correlationKey")?.asText()
            ?: throw IllegalArgumentException("MessageEvent $nodeId missing correlationKey")

        val correlationKey = variableManager.evaluateCorrelationKey(correlationKeyTemplate, instance)
        val timeoutSeconds = properties.get("timeoutSeconds")?.asLong()
        val timeoutAt = timeoutSeconds?.let { LocalDateTime.now().plusSeconds(it) }

        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            timeoutAt = timeoutAt
        )
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.MESSAGE_WAITING,
            message = "Waiting for message '$messageName' with correlation key '$correlationKey'."
        )

        try {
            rabbitPublisher.publishMessageExpected(
                processInstanceId = instance.id,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                timeoutSeconds = timeoutSeconds
            )
        } catch (_: Exception) {
        }

        touchInstance(instance)
    }

    fun handleTimerEvent(instance: ProcessInstance, node: JsonNode, timerMessageName: String) {
        val nodeId = node.get("id").asText()
        val properties = node.get("properties")
            ?: throw IllegalArgumentException("TimerEvent $nodeId missing properties")

        val timeoutSeconds = properties.get("timeoutSeconds")?.asLong()
            ?: throw IllegalArgumentException("TimerEvent $nodeId missing timeoutSeconds")

        require(timeoutSeconds > 0) { "TimerEvent $nodeId timeoutSeconds must be > 0" }

        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = timerMessageName,
            correlationKey = "timer-${instance.id}-$nodeId",
            timeoutAt = LocalDateTime.now().plusSeconds(timeoutSeconds)
        )

        touchInstance(instance)
    }

    fun handleMessageIntermediateCatchEvent(instance: ProcessInstance, node: JsonNode) {
        val nodeId = node.get("id").asText()
        val message = node.get("message")
            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message' object")

        val messageName = message.get("name")?.asText()
            ?: throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            throw IllegalArgumentException("MessageIntermediateCatchEvent $nodeId missing 'message.correlationKeys'")
        }

        val correlationKey = variableManager.evaluateCorrelationKey(correlationKeys[0].asText(), instance)

        messageSubscriptionService.subscribeToMessage(
            processInstanceId = instance.id,
            nodeId = nodeId,
            messageName = messageName,
            correlationKey = correlationKey,
            timeoutAt = null
        )

        try {
            rabbitPublisher.publishMessageExpected(
                processInstanceId = instance.id,
                nodeId = nodeId,
                messageName = messageName,
                correlationKey = correlationKey,
                timeoutSeconds = null
            )
        } catch (_: Exception) {
        }

        touchInstance(instance)
    }

    fun publishMessageIntermediateThrowEvent(instance: ProcessInstance, node: JsonNode) {
        val nodeId = node.get("id").asText()
        val message = node.get("message")
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message' object")

        val messageName = message.get("name")?.asText()
            ?: throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message.name'")

        val correlationKeys = message.get("correlationKeys")
        if (correlationKeys == null || !correlationKeys.isArray || correlationKeys.size() == 0) {
            throw IllegalArgumentException("MessageIntermediateThrowEvent $nodeId missing 'message.correlationKeys'")
        }

        val payload = buildThrowPayload(instance, message.get("payload"))

        try {
            rabbitPublisher.publishMessageThrown(
                messageName = messageName,
                correlationKey = variableManager.evaluateCorrelationKey(correlationKeys[0].asText(), instance),
                variables = payload
            )
        } catch (ex: Exception) {
            // Keep process behavior compatible with the previous best-effort publish.
        }
    }

    private fun buildThrowPayload(instance: ProcessInstance, payloadArray: JsonNode?): Map<String, Any> {
        val payload = mutableMapOf<String, Any>()

        if (payloadArray != null && payloadArray.isArray) {
            payloadArray.forEach { payloadMapping ->
                val targetName = payloadMapping.get("targetName")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'targetName'")

                val source = payloadMapping.get("source")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'source'")

                val value = payloadMapping.get("value")?.asText()
                    ?: throw IllegalArgumentException("MessageIntermediateThrowEvent payload missing 'value'")

                val mappedValue = when (source) {
                    "variable" -> {
                        processVariableRepository.findByProcessInstanceIdAndName(instance.id, value)
                            ?.value?.asText() ?: value
                    }
                    "static" -> value
                    else -> value
                }

                payload[targetName] = mappedValue
            }
        }

        return payload
    }

    private fun touchInstance(instance: ProcessInstance) {
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)
    }
}
