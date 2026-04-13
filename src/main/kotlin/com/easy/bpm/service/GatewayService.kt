package com.easy.bpm.service

import com.easy.bpm.enum.NodeType
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import javax.script.ScriptEngineManager
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Service

@Service
class GatewayService(
    private val processVariableRepository: ProcessVariableRepository,
    private val objectMapper: ObjectMapper
) {

    fun getNextNodes(node: JsonNode, definition: JsonNode, instance: ProcessInstance): List<String> {
        val nodeType = NodeType.fromString(node.get("type").asText())

        if (nodeType == NodeType.ParallelGateway) {
            return node.get("next")?.map { it.asText() } ?: emptyList()
        }

        if (nodeType != NodeType.ExclusiveGateway) {
            return node.get("next")?.map { it.asText() } ?: emptyList()
        }

        val flows = definition.get("flows") ?: return node.get("next")?.map { it.asText() } ?: emptyList()

        val nodeId = node.get("id").asText()
        val outgoing = flows.filter {
            val s = it.get("source") ?: it.get("from")
            s?.asText() == nodeId
        }

        for (flow in outgoing) {
            val target = flow.get("target")?.asText() ?: flow.get("to")?.asText() ?: continue
            val conditionNode = flow.get("condition") ?: flow.get("expression")

            val passes = if (conditionNode == null || conditionNode.isNull) {
                true
            } else {
                val conditionText = conditionNode.asText()
                try {
                    evaluateCondition(conditionText, instance)
                } catch (ex: Exception) {
                    throw IllegalArgumentException("Failed to evaluate condition '$conditionText' for flow to '$target': ${ex.message}")
                }
            }

            if (!passes) continue

            val targetNode = definition.get("nodes").find { it.get("id").asText() == target }
            if (targetNode != null && NodeType.fromString(targetNode.get("type").asText()) == NodeType.ParallelGateway) {
                val incoming = definition.get("flows").filter { (it.get("target") ?: it.get("to"))?.asText() == target }.map { (it.get("source") ?: it.get("from"))?.asText() }
                    .filterNotNull()
                if (incoming.size <= 1) {
                    return listOf(target)
                }

                val tokenVarName = "__pg_${target}_arrivals"
                val existing = processVariableRepository.findByProcessInstanceIdAndName(instance.id, tokenVarName)
                val currentCount = existing?.value?.asInt() ?: 0
                val newCount = currentCount + 1

                if (existing != null) {
                    existing.value = objectMapper.valueToTree(newCount)
                    processVariableRepository.save(existing)
                } else {
                    processVariableRepository.save(ProcessVariable(processInstanceId = instance.id, name = tokenVarName, value = objectMapper.valueToTree(newCount)))
                }

                if (newCount >= incoming.size) {
                    val reset = processVariableRepository.findByProcessInstanceIdAndName(instance.id, tokenVarName)
                    if (reset != null) {
                        reset.value = objectMapper.valueToTree(0)
                        processVariableRepository.save(reset)
                    }
                    return listOf(target)
                } else {
                    continue
                }
            }

            return listOf(target)
        }

        val elseFlow = outgoing.find { (it.get("condition") ?: it.get("expression")) == null || (it.get("condition") ?: it.get("expression"))?.isNull == true }
        if (elseFlow != null) {
            val t = elseFlow.get("target")?.asText() ?: elseFlow.get("to")?.asText()
            if (!t.isNullOrBlank()) return listOf(t)
        }

        return node.get("next")?.map { it.asText() } ?: emptyList()
    }

    fun evaluateCondition(condition: String, instance: ProcessInstance): Boolean {
        var expr = condition

        val regex = Regex("\\$\\{([^}]+)\\}")
        expr = regex.replace(expr) { match ->
            val varName = match.groupValues[1]
            val procVar = processVariableRepository.findByProcessInstanceIdAndName(instance.id, varName)
            val node = procVar?.value
            when {
                node == null || node.isNull -> "null"
                node.isTextual -> {
                    val text = node.asText()
                    if (text.matches(Regex("-?\\d+(\\.\\d+)?"))) {
                        text
                    } else {
                        "\"${text.replace("\"","\\\"") }\""
                    }
                }
                else -> node.toString()
            }
        }

        return try {
            val engine = ScriptEngineManager().getEngineByName("JavaScript")
            if (engine != null) {
                val result = engine.eval(expr)
                when (result) {
                    is Boolean -> result
                    is Number -> result.toInt() != 0
                    else -> result != null
                }
            } else {
                val parser = SpelExpressionParser()
                val context = StandardEvaluationContext()
                val spelExpr = parser.parseExpression(expr)
                val value = spelExpr.getValue(context)
                when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    else -> value != null
                }
            }
        } catch (ex: Exception) {
            System.out.println(ex.message)
            throw ex
        }
    }
}
