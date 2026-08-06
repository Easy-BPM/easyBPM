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

import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessInstanceLifecycleManager(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val taskRepository: TaskRepository,
    private val workerRequestRepository: WorkerRequestRepository,
    private val objectMapper: ObjectMapper,
    private val messageSubscriptionService: MessageSubscriptionService,
    private val metricsService: MetricsService,
    private val timelineService: ProcessInstanceTimelineService,
    private val userTaskHandler: ProcessUserTaskHandler
) {
    @Transactional
    fun moveProcessNode(processInstanceId: Long, fromNode: String, toNode: String): ProcessInstance {
        val instance = processInstanceRepository.findByIdForUpdate(processInstanceId)
            ?: throw IllegalArgumentException("Process instance not found")

        val currentNodes = instance.currentNode ?: emptyList()
        if (!currentNodes.contains(fromNode)) {
            throw IllegalArgumentException("Instance is not currently at node '$fromNode'")
        }

        val definition = parseDefinition(instance.processDefinition.definitionJson)
        val toNodeDefinition = findNode(definition, toNode)

        syncTasksForManualMove(instance, fromNode, toNode, toNodeDefinition)

        val movedNodes = currentNodes.map { if (it == fromNode) toNode else it }
        instance.currentNode = movedNodes
        if (instance.nodeHistory.lastOrNull() != toNode) {
            instance.nodeHistory = instance.nodeHistory + toNode
        }
        instance.updatedAt = LocalDateTime.now()

        val saved = processInstanceRepository.save(instance)
        timelineService.record(
            processInstanceId = processInstanceId,
            nodeId = toNode,
            eventType = ProcessInstanceEventType.MANUAL_MOVE,
            message = "Token moved from '$fromNode' to '$toNode'.",
            details = "fromNode=$fromNode; toNode=$toNode"
        )
        timelineService.record(
            processInstanceId = processInstanceId,
            nodeId = toNode,
            eventType = ProcessInstanceEventType.NODE_ENTERED,
            message = "Entered node '$toNode'."
        )
        return saved
    }

    @Transactional
    fun stopProcessInstance(id: Long): ProcessInstance {
        val instance = processInstanceRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        if (instance.status != ProcessStatus.ACTIVE) {
            return instance
        }

        val pendingTasks = taskRepository.findByProcessInstanceId(id)
            .filter { it.status == TaskStatus.PENDING }
        pendingTasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
            taskRepository.delete(task)
        }

        messageSubscriptionService.deleteSubscriptionsForInstance(id)

        instance.status = ProcessStatus.CANCELLED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        val saved = processInstanceRepository.save(instance)
        timelineService.record(
            processInstanceId = id,
            eventType = ProcessInstanceEventType.PROCESS_CANCELLED,
            message = "Process instance cancelled."
        )
        return saved
    }

    @Transactional
    fun deleteProcessInstance(id: Long) {
        val instance = processInstanceRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        val tasks = taskRepository.findByProcessInstanceId(id)
        tasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
        }

        taskRepository.deleteByProcessInstanceId(id)
        processVariableRepository.deleteByProcessInstanceId(id)
        messageSubscriptionService.deleteSubscriptionsForInstance(id)
        workerRequestRepository.deleteByProcessInstanceId(id)
        processInstanceRepository.delete(instance)
    }

    fun finishProcess(instance: ProcessInstance) {
        instance.status = ProcessStatus.COMPLETED
        instance.currentNode = emptyList()
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        metricsService.recordProcessCompleted()
        timelineService.record(
            processInstanceId = instance.id,
            eventType = ProcessInstanceEventType.PROCESS_COMPLETED,
            message = "Process instance completed."
        )
    }

    private fun syncTasksForManualMove(
        instance: ProcessInstance,
        fromNode: String,
        toNode: String,
        toNodeDefinition: JsonNode
    ) {
        val fromPendingTasks = taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
            instance.id,
            fromNode,
            TaskStatus.PENDING
        )

        fromPendingTasks.forEach { task ->
            taskVariableRepository.deleteByTaskId(task.id)
            taskRepository.delete(task)
        }

        val targetType = NodeType.fromString(toNodeDefinition.get("type").asText())
        if (targetType != NodeType.UserTask) {
            return
        }

        val existingTargetTasks = taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
            instance.id,
            toNode,
            TaskStatus.PENDING
        )

        if (existingTargetTasks.isEmpty()) {
            userTaskHandler.handleUserTask(instance, toNodeDefinition)
        }
    }

    private fun parseDefinition(definitionJson: String): JsonNode =
        objectMapper.readTree(definitionJson)

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalArgumentException("Node '$nodeId' not found")
}
