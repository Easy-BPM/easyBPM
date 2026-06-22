package com.easy.bpm.messaging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class RabbitPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper
) {
    fun publishServiceTaskRequest(processInstanceId: Long, nodeId: String, properties: JsonNode) {
        val payload = mapOf(
            "processInstanceId" to processInstanceId,
            "nodeId" to nodeId,
            "properties" to objectMapper.convertValue(properties, Map::class.java)
        )

        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.REQUEST_ROUTING_KEY, payload)
    }

    fun publishServiceTaskCompletion(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val payload = mapOf(
            "processInstanceId" to processInstanceId,
            "nodeId" to nodeId,
            "outputs" to outputs
        )

        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.COMPLETION_ROUTING_KEY, payload)
    }

    fun publishTaskCreated(payload: Map<String, Any?>) {
        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.TASK_CREATED_ROUTING_KEY, payload)
    }

    fun publishTaskCompleted(payload: Map<String, Any?>) {
        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.TASK_COMPLETED_ROUTING_KEY, payload)
    }

    fun publishMessageExpected(
        processInstanceId: Long,
        nodeId: String,
        messageName: String,
        correlationKey: String,
        timeoutSeconds: Long? = null
    ) {
        val payload = mapOf(
            "processInstanceId" to processInstanceId,
            "nodeId" to nodeId,
            "messageName" to messageName,
            "correlationKey" to correlationKey,
            "timeoutSeconds" to timeoutSeconds
        )

        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.MESSAGE_EXPECTED_ROUTING_KEY, payload)
    }

    fun publishMessageReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ) {
        val payload = mapOf(
            "messageName" to messageName,
            "correlationKey" to correlationKey,
            "variables" to variables
        )

        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.MESSAGE_EVENTS_ROUTING_KEY, payload)
    }

    fun publishMessageThrown(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>
    ) {
        val payload = mapOf(
            "messageName" to messageName,
            "correlationKey" to correlationKey,
            "variables" to variables
        )

        rabbitTemplate.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.MESSAGE_EVENTS_ROUTING_KEY, payload)
    }
}

