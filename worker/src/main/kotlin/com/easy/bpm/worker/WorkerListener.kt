package com.easy.bpm.worker

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class WorkerListener(
    private val rabbitTemplate: RabbitTemplate
) {

    companion object {
        const val EXCHANGE = "bpm.exchange"
        const val REQUEST_ROUTING_KEY = "service.task.request"
        const val COMPLETION_ROUTING_KEY = "service.task.completed"
        const val REQUEST_QUEUE = "service-task-requests"
    }

    private val restTemplate = RestTemplate()

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

        // Build request from properties (simple support: url, method, headers, body)
        val url = properties["url"] as? String ?: throw IllegalArgumentException("Missing url")
        val method = (properties["method"] as? String ?: "POST").uppercase()

        val headers = HttpHeaders()
        (properties["headers"] as? Map<*, *>)?.forEach { (k, v) -> headers[k.toString()] = v.toString() }

        val body = properties["body"] ?: emptyMap<String, Any>()

        val entity = HttpEntity(body, headers)

        val response: Map<*, *>? = when (method) {
            "POST" -> restTemplate.postForEntity(url, entity, Map::class.java).body
            "PUT" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map::class.java).body
            "DELETE" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, Map::class.java).body
            "GET" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
            else -> throw IllegalArgumentException("Unsupported method $method")
        }

        val outputs = (response as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()

        val payload = mapOf(
            "processInstanceId" to processInstanceId,
            "nodeId" to nodeId,
            "outputs" to outputs
        )

        rabbitTemplate.convertAndSend(EXCHANGE, COMPLETION_ROUTING_KEY, payload)
    }
}
