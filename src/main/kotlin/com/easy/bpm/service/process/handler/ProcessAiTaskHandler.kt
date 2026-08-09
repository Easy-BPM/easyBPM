package com.easy.bpm.service.process.handler

import com.easy.bpm.handler.AITaskExecutionException
import com.easy.bpm.handler.AITaskHandler
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.process.ProcessVariableManager
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessAiTaskHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val aiTaskHandler: AITaskHandler,
    private val variableManager: ProcessVariableManager
) {
    private val logger = LoggerFactory.getLogger(ProcessAiTaskHandler::class.java)

    fun handleAiTask(instance: ProcessInstance, node: JsonNode) {
        val nodeId = node.get("id").asText()

        try {
            val variables = processVariableRepository.findByProcessInstanceId(instance.id)
                .associateBy({ it.name }, { it.value })

            val outputVars = aiTaskHandler.executeAITask(
                instanceId = instance.id,
                node = node,
                inputVariables = variables
            )

            outputVars.forEach { (varName, varValue) ->
                variableManager.assignProcessVariables(instance.id, mapOf(varName to varValue))
            }

            logger.info("AI Task completed successfully: instance=${instance.id}, nodeId=$nodeId")

            instance.updatedAt = LocalDateTime.now()
            processInstanceRepository.save(instance)
        } catch (ex: AITaskExecutionException) {
            logger.error("AI Task execution failed: instance=${instance.id}, nodeId=$nodeId, errorCode=${ex.errorCode}", ex)
            throw ex
        }
    }
}
