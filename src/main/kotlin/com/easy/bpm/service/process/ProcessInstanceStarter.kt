package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class ProcessInstanceStarter(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService,
    private val timelineService: ProcessInstanceTimelineService,
    private val variableManager: ProcessVariableManager,
    private val navigator: ProcessNavigator,
    private val executionEngine: ProcessExecutionEngine
) {
    @Transactional
    fun startWithDefinition(
        definition: ProcessDefinition,
        initialVariables: Map<String, Any> = emptyMap(),
        startNodeId: String? = null
    ): ProcessInstance {
        val startTime = System.currentTimeMillis()
        val json = objectMapper.readTree(definition.definitionJson)

        val instance = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )
        )

        metricsService.recordProcessStarted()
        timelineService.record(
            processInstanceId = instance.id,
            eventType = ProcessInstanceEventType.PROCESS_STARTED,
            message = "Process instance started."
        )

        variableManager.initializeProcessVariables(instance, json)
        initialVariables.forEach { (name, value) ->
            variableManager.upsertProcessVariable(instance.id, name, objectMapper.valueToTree(value))
        }

        val startNodes = navigator.getStartNodes(instance, json, startNodeId)
        instance.currentNode = startNodes
        instance.nodeHistory = startNodes
        processInstanceRepository.save(instance)

        startNodes.forEach { nodeId ->
            timelineService.record(
                processInstanceId = instance.id,
                nodeId = nodeId,
                eventType = ProcessInstanceEventType.NODE_ENTERED,
                message = "Entered node '$nodeId'."
            )
        }

        executionEngine.executeNodes(startNodes, instance, json)

        val duration = System.currentTimeMillis() - startTime
        metricsService.recordProcessExecution(duration)

        return instance
    }
}
