package com.easy.bpm.service.integration

import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

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
        val resolvedUrl = renderTemplate(url, vars, nodeId)

        // 🔑 Corpo pode vir direto do config ou ser todo o mapa de variáveis
        val body: Any? = if (config.has("body")) {
            objectMapper.convertValue(config.get("body"), Map::class.java)
        } else {
            vars
        }

        val entity = org.springframework.http.HttpEntity(body, headers)
        val requestUri = URI.create(resolvedUrl)

        val response: Map<*, *>? = when (method) {
            "POST" -> restTemplate.postForEntity(requestUri, entity, Map::class.java).body
            "PUT" -> restTemplate.exchange(requestUri, org.springframework.http.HttpMethod.PUT, entity, Map::class.java).body
            "DELETE" -> restTemplate.exchange(requestUri, org.springframework.http.HttpMethod.DELETE, entity, Map::class.java).body
            "GET" -> restTemplate.exchange(requestUri, org.springframework.http.HttpMethod.GET, entity, Map::class.java).body
            else -> throw IllegalArgumentException("Unsupported method $method")
        }

        // ✅ Save or update output variables in process
        val rawOutputs = response?.entries
            ?.filter { it.key is String }
            ?.associate { it.key as String to it.value }
            ?: emptyMap()
        rawOutputs.forEach { (k, v) ->
            val value = objectMapper.valueToTree<JsonNode>(v)
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

        return rawOutputs.mapValues { (_, value) -> outputValueAsString(value) }
    }

    private fun renderTemplate(template: String, variables: Map<String, JsonNode>, nodeId: String): String {
        var rendered = template
        variables.forEach { (name, value) ->
            val replacement = if (value.isTextual) value.asText() else value.toString()
            rendered = rendered
                .replace("{{$name}}", replacement)
                .replace("\${$name}", replacement)
        }
        val missingVariables = doubleBraceVariableRegex.findAll(rendered).map { it.groupValues[1] }
            .plus(dollarBraceVariableRegex.findAll(rendered).map { it.groupValues[1] })
            .distinct()
            .toList()
        if (missingVariables.isNotEmpty()) {
            throw IllegalArgumentException(
                "Integration node $nodeId URL references missing process variable(s): ${missingVariables.joinToString(", ")}"
            )
        }
        return rendered
    }

    private fun outputValueAsString(value: Any?): String =
        when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }

    companion object {
        private val doubleBraceVariableRegex = Regex("\\{\\{([^}]+)}}")
        private val dollarBraceVariableRegex = Regex("\\$\\{([^}]+)}")
    }
}
