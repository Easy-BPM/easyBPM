package com.easy.bpm.messaging

import com.easy.bpm.service.process.ProcessService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class RabbitListenerService(
    private val processService: ProcessService
) {

    @RabbitListener(queues = [AmqpConfig.SERVICE_TASK_COMPLETIONS_QUEUE])
    fun onServiceTaskCompleted(message: Map<String, Any>) {
        try {
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
        } catch (ex: Exception) {
            // Log the error but don't re-throw; stale messages should be consumed gracefully
            System.err.println("Failed to process completion message: ${ex.message}")
            ex.printStackTrace()
        }
    }

    @RabbitListener(queues = [AmqpConfig.SERVICE_TASK_DLQ])
    fun onServiceTaskFailed(message: Map<String, Any>) {
        try {
            val nodeId = message["nodeId"] as? String
            if (nodeId == null) {
                System.err.println("DLQ: Missing nodeId in message: $message")
                return
            }

            val processInstanceIdAny = message["processInstanceId"]
            val processInstanceId: Long? = when (processInstanceIdAny) {
                is Int -> processInstanceIdAny.toLong()
                is Long -> processInstanceIdAny
                is String -> {
                    try {
                        processInstanceIdAny.toLong()
                    } catch (e: NumberFormatException) {
                        System.err.println("DLQ: Invalid processInstanceId format: $processInstanceIdAny")
                        null
                    }
                }
                else -> {
                    System.err.println("DLQ: Invalid processInstanceId type: ${processInstanceIdAny?.javaClass}")
                    null
                }
            }
            
            if (processInstanceId == null) {
                return
            }

            val errorMessage = message["dlqReason"]?.toString() ?: "Unknown error"
            System.out.println("DLQ: Processing failed service task - instanceId=$processInstanceId, nodeId=$nodeId, reason=$errorMessage")
            
            processService.handleServiceTaskFailed(processInstanceId, nodeId, errorMessage)
            System.out.println("DLQ: Successfully processed failure for instance=$processInstanceId")
        } catch (ex: Exception) {
            // Log the error but don't re-throw; we don't want to nack the message
            // This prevents stale messages from blocking the listener indefinitely
            System.err.println("DLQ: Critical error processing DLQ message: ${ex.message}")
            System.err.println("DLQ: Message was: $message")
            ex.printStackTrace()
        }
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
