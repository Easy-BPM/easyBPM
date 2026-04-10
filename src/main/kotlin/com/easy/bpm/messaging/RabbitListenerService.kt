package com.easy.bpm.messaging

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class RabbitListenerService(
    private val processService: com.easy.bpm.service.ProcessService
) {

    @RabbitListener(queues = [AmqpConfig.SERVICE_TASK_COMPLETIONS_QUEUE])
    fun onServiceTaskCompleted(message: Map<String, Any>) {
        val processInstanceIdAny = message["processInstanceId"]
        val nodeId = message["nodeId"] as? String ?: return

        val processInstanceId = when (processInstanceIdAny) {
            is Int -> processInstanceIdAny.toLong()
            is Long -> processInstanceIdAny
            is String -> processInstanceIdAny.toLong()
            else -> throw IllegalArgumentException("Invalid processInstanceId type: ${processInstanceIdAny?.javaClass}")
        }

        val outputsAny = message["outputs"]
        val outputs = when (outputsAny) {
            is Map<*, *> -> outputsAny.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
            else -> emptyMap()
        }

        processService.handleServiceTaskCompleted(processInstanceId, nodeId, outputs)
    }

    @RabbitListener(queues = [AmqpConfig.MESSAGE_EVENTS_QUEUE])
    fun onMessageReceived(message: Map<String, Any>) {
        val messageName = message["messageName"] as? String ?: return
        val correlationKey = message["correlationKey"] as? String ?: return

        @Suppress("UNCHECKED_CAST")
        val variables = message["variables"] as? Map<String, Any>

        try {
            processService.handleMessageReceived(messageName, correlationKey, variables)
        } catch (ex: IllegalArgumentException) {
            // Log or handle message with no matching subscription
            ex.printStackTrace()
        }
    }
}
