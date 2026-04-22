package com.easy.bpm.service

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class IntegrationService(
    private val restTemplate: RestTemplate,
    private val processVariableRepository: ProcessVariableRepository,
    private val objectMapper: ObjectMapper
) {
    fun executeIntegration(
        instance: ProcessInstance,
        nodeId: String,
        config: JsonNode
    ): Map<String, String> {
        val url = config.get("url")?.asText()
            ?: throw IllegalArgumentException("Integration node $nodeId missing 'url'")
        val method = config.get("method")?.asText("POST")

        // 🔑 Headers opcionais
        val headers = org.springframework.http.HttpHeaders()
        config.get("headers")?.fields()?.forEach { (k, v) ->
            headers[k] = v.asText()
        }

        // 🔎 Variáveis do processo para preencher o corpo
        val vars = processVariableRepository.findByProcessInstanceId(instance.id)
            .associate { it.name to it.value }

        // 🔑 Corpo pode vir direto do config ou ser todo o mapa de variáveis
        val body: Any? = if (config.has("body")) {
            objectMapper.convertValue(config.get("body"), Map::class.java)
        } else {
            vars
        }

        val entity = org.springframework.http.HttpEntity(body, headers)

        val response: Map<*, *>? = when (method) {
            "POST" -> restTemplate.postForEntity(url, entity, Map::class.java).body
            "PUT" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map::class.java).body
            "DELETE" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, Map::class.java).body
            "GET" -> restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
            else -> throw IllegalArgumentException("Unsupported method $method")
        }

        // ✅ Save or update output variables in process
        val outputs = (response as? Map<String, Any>)?.mapValues { it.value.toString() } ?: emptyMap()
        outputs.forEach { (k, v) ->
            val value = objectMapper.readTree(v as? String ?: v.toString())
            val existing = processVariableRepository.findByProcessInstanceIdAndName(instance.id, k)
            
            if (existing != null) {
                existing.value = value
                processVariableRepository.save(existing)
            } else {
                processVariableRepository.save(
                    ProcessVariable(
                        processInstanceId = instance.id,
                        name = k,
                        value = value
                    )
                )
            }
        }

        return outputs
    }
}

