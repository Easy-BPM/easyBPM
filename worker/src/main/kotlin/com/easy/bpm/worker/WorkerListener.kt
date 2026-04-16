package com.easy.bpm.worker

import com.easy.bpm.model.worker.WorkerRequest
import com.easy.bpm.model.worker.WorkerRequestStatus
import com.easy.bpm.repository.worker.WorkerRequestRepository
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestClientException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.logging.Logger

@Service
class WorkerListener(
    private val rabbitTemplate: RabbitTemplate,
    private val workerRequestRepository: WorkerRequestRepository
) {

    companion object {
        const val EXCHANGE = "bpm.exchange"
        const val REQUEST_ROUTING_KEY = "service.task.request"
        const val COMPLETION_ROUTING_KEY = "service.task.completed"
        const val DLQ_ROUTING_KEY = "service.task.request.dlq"
        const val REQUEST_QUEUE = "service-task-requests"
        const val MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY_MS = 5000
    }

    private val restTemplate = RestTemplate()
    private val logger = Logger.getLogger(WorkerListener::class.java.name)

    @RabbitListener(queues = [REQUEST_QUEUE])
    fun onRequest(message: Map<String, Any>) {
        val processInstanceIdAny = message["processInstanceId"]
        val nodeId = message["nodeId"] as? String ?: return
        val properties = message["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()

        val processInstanceId = when (processInstanceIdAny) {
            is Int -> processInstanceIdAny.toLong()
            is Long -> processInstanceIdAny
            is String -> processInstanceIdAny.toLong()
            else -> throw IllegalArgumentException("Invalid processInstanceId type: ${processInstanceIdAny?.javaClass}")
        }

        // Generate idempotency key
        val idempotencyKey = generateIdempotencyKey(processInstanceId, nodeId)

        // Check if already processed (idempotency)
        val existing = workerRequestRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            when (existing.status) {
                WorkerRequestStatus.COMPLETED -> {
                    // Already completed, send completion again
                    sendCompletion(existing.processInstanceId, existing.nodeId, existing.lastError?.let { mapOf() } ?: mapOf())
                    return
                }
                WorkerRequestStatus.IN_PROGRESS -> {
                    // Currently processing, ignore duplicate
                    logger.warning("Ignoring duplicate request for $idempotencyKey (in progress)")
                    return
                }
                WorkerRequestStatus.FAILED, WorkerRequestStatus.PENDING -> {
                    // Can retry
                }
                WorkerRequestStatus.DLQ -> {
                    // Already in DLQ, ignore
                    return
                }
            }
        }

        // Create or update worker request tracker
        val workerRequest = existing ?: WorkerRequest(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            idempotencyKey = idempotencyKey
        )

        try {
            // Perform the work
            val result = executeRequest(processInstanceId, nodeId, properties)

            // Mark as completed
            workerRequest.status = WorkerRequestStatus.COMPLETED
            workerRequest.completedAt = LocalDateTime.now()
            workerRequest.lastAttemptAt = LocalDateTime.now()
            workerRequestRepository.save(workerRequest)

            // Send completion
            sendCompletion(processInstanceId, nodeId, result)

            logger.info("Successfully completed work for $idempotencyKey")

        } catch (ex: Exception) {
            workerRequest.lastError = ex.message?.take(1000)
            workerRequest.lastAttemptAt = LocalDateTime.now()
            workerRequest.retryCount++

            if (workerRequest.retryCount >= MAX_RETRIES) {
                // Max retries exceeded, send to DLQ
                workerRequest.status = WorkerRequestStatus.DLQ
                workerRequestRepository.save(workerRequest)

                logger.severe("Max retries exceeded for $idempotencyKey, routing to DLQ: ${ex.message}")
                routeToDlq(message, ex.message ?: "Unknown error")

            } else {
                // Schedule retry with exponential backoff
                val backoffMs = INITIAL_RETRY_DELAY_MS.toLong() * (1L shl (workerRequest.retryCount - 1))
                workerRequest.status = WorkerRequestStatus.PENDING
                workerRequestRepository.save(workerRequest)

                logger.warning("Retry ${workerRequest.retryCount}/$MAX_RETRIES for $idempotencyKey in ${backoffMs}ms: ${ex.message}")
                scheduleRetry(message, backoffMs)
            }
        }
    }

    private fun executeRequest(
        processInstanceId: Long,
        nodeId: String,
        properties: Map<*, *>
    ): Map<String, String> {
        var url = properties["url"] as? String ?: throw IllegalArgumentException("Missing url")
        val method = (properties["method"] as? String ?: "POST").uppercase()

        val headers = HttpHeaders()
        (properties["headers"] as? Map<*, *>)?.forEach { (k, v) -> headers[k.toString()] = v.toString() }

        val auth = properties["auth"] as? Map<*, *>
        if (auth != null) {
            val authType = auth["type"]?.toString()?.trim()?.lowercase()
                ?: throw IllegalArgumentException("Auth is missing 'type'")
            val authRef = auth["ref"]?.toString()?.trim().orEmpty()
            if (authRef.isEmpty()) {
                throw IllegalArgumentException("Auth is missing 'ref'")
            }

            when (authType) {
                "bearer" -> {
                    val token = resolveEnv(authRef)
                    headers.setBearerAuth(token)
                }
                "basic" -> {
                    val username = resolveEnv("${authRef}_USERNAME")
                    val password = resolveEnv("${authRef}_PASSWORD")
                    headers.setBasicAuth(username, password)
                }
                "apikey" -> {
                    val apiKeyValue = resolveEnv(authRef)
                    val keyName = auth["key"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "X-API-Key"
                    val target = auth["in"]?.toString()?.trim()?.lowercase() ?: "header"

                    if (target == "query") {
                        url = UriComponentsBuilder
                            .fromHttpUrl(url)
                            .queryParam(keyName, apiKeyValue)
                            .build(true)
                            .toUriString()
                    } else {
                        headers[keyName] = apiKeyValue
                    }
                }
                else -> throw IllegalArgumentException("Unsupported auth type '$authType'")
            }
        }

        val body = properties["body"] ?: emptyMap<String, Any>()
        val entity = HttpEntity(body, headers)

        val response: Map<*, *>? = when (method) {
            "POST" -> restTemplate.postForEntity(url, entity, Map::class.java).body
            "PUT" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map::class.java).body
            "DELETE" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, Map::class.java).body
            "GET" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
            else -> throw IllegalArgumentException("Unsupported method $method")
        }

        return (response as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()
    }

    private fun resolveEnv(name: String): String {
        return System.getenv(name)
            ?: throw IllegalArgumentException("Missing environment variable '$name' for API task auth")
    }

    private fun sendCompletion(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val payload = mapOf(
            "processInstanceId" to processInstanceId,
            "nodeId" to nodeId,
            "outputs" to outputs
        )
        rabbitTemplate.convertAndSend(EXCHANGE, COMPLETION_ROUTING_KEY, payload)
    }

    private fun scheduleRetry(message: Map<String, Any>, delayMs: Long) {
        // For now, requeue directly. In production, use RabbitMQ delayed exchange plugin or external scheduler
        Thread {
            Thread.sleep(delayMs)
            rabbitTemplate.convertAndSend(EXCHANGE, REQUEST_ROUTING_KEY, message)
        }.start()
    }

    private fun routeToDlq(message: Map<String, Any>, reason: String) {
        val dlqMessage = mapOf(
            *message.toList().toTypedArray(),
            "dlqReason" to reason,
            "dlqTimestamp" to LocalDateTime.now().toString()
        )
        rabbitTemplate.convertAndSend(EXCHANGE, DLQ_ROUTING_KEY, dlqMessage)
    }

    private fun generateIdempotencyKey(processInstanceId: Long, nodeId: String): String {
        val data = "$processInstanceId:$nodeId"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

