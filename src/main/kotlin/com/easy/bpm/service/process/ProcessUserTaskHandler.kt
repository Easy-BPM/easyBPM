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

import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.task.TaskRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

@Component
class ProcessUserTaskHandler(
    private val formService: FormService,
    private val taskRepository: TaskRepository,
    private val rabbitPublisher: RabbitPublisher,
    private val metricsService: MetricsService,
    private val variableManager: ProcessVariableManager,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun handleUserTask(instance: ProcessInstance, node: JsonNode) {
        val form = resolveUserTaskForm(node)
        val task = taskRepository.save(
            Task(
                processInstanceId = instance.id,
                title = node.get("name").asText(),
                nodeId = node.get("id").asText(),
                formId = form?.id
            )
        )

        metricsService.recordTaskCreated(task.nodeId)
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = task.nodeId,
            eventType = ProcessInstanceEventType.TASK_CREATED,
            message = "Task '${task.title ?: task.nodeId}' created.",
            details = "taskId=${task.id}"
        )

        try {
            rabbitPublisher.publishTaskCreated(
                mapOf(
                    "taskId" to task.id,
                    "processInstanceId" to task.processInstanceId,
                    "nodeId" to task.nodeId,
                    "title" to task.title,
                    "formDbId" to task.formId,
                    "formId" to form?.formId
                )
            )
        } catch (_: Exception) {
        }

        variableManager.applyTaskInputs(task, node, instance)
    }

    private fun resolveUserTaskForm(node: JsonNode) =
        node.get("config")?.get("formId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredFormRef ->
            formService.getLatestVersionByFormId(configuredFormRef)
                ?: configuredFormRef.toLongOrNull()?.let(formService::getById)
                ?: formService.getLatestVersionByName(configuredFormRef)
        } ?: formService.getLatestVersionByName(node.get("id").asText())
}
