package com.easy.bpm.service.admin

import com.easy.bpm.controller.data.MaintenanceCleanupSummary
import com.easy.bpm.controller.data.PurgeCompletedInstancesRequest
import com.easy.bpm.controller.data.PurgeCompletedTasksRequest
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.codetask.CodeTaskExecutionAuditRepository
import com.easy.bpm.repository.document.DocumentRepository
import com.easy.bpm.repository.incident.IncidentEventRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AdminMaintenanceService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val taskRepository: TaskRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val documentRepository: DocumentRepository,
    private val messageSubscriptionRepository: MessageSubscriptionRepository,
    private val workerRequestRepository: WorkerRequestRepository,
    private val codeTaskExecutionAuditRepository: CodeTaskExecutionAuditRepository,
    private val incidentRepository: IncidentRepository,
    private val incidentEventRepository: IncidentEventRepository,
    private val timelineRepository: ProcessInstanceEventRepository,
    private val callActivityMappingRepository: CallActivityMappingRepository
) {
    companion object {
        private const val DEFAULT_PURGE_BATCH_SIZE = 500
        private const val MAX_PURGE_BATCH_SIZE = 10_000
    }

    fun previewPurgeCompletedInstances(request: PurgeCompletedInstancesRequest): MaintenanceCleanupSummary =
        purgeCompletedInstances(request.copy(dryRun = true))

    fun previewPurgeCompletedTasks(
        request: PurgeCompletedTasksRequest,
        excludedProcessInstanceIds: Collection<Long> = emptyList()
    ): MaintenanceCleanupSummary =
        purgeCompletedTasks(request.copy(dryRun = true), excludedProcessInstanceIds)

    @Transactional
    fun purgeCompletedInstances(request: PurgeCompletedInstancesRequest): MaintenanceCleanupSummary {
        val batchSize = (request.batchSize ?: DEFAULT_PURGE_BATCH_SIZE)
            .coerceIn(1, MAX_PURGE_BATCH_SIZE)
        val candidateIds = processInstanceRepository.findPurgeCandidateIds(
            status = ProcessStatus.COMPLETED,
            before = request.completedBefore,
            processDefinitionId = request.processDefinitionId,
            processKey = request.processKey?.takeIf { it.isNotBlank() },
            pageable = PageRequest.of(0, batchSize)
        )

        return cleanupInstances(candidateIds, dryRun = request.dryRun)
    }

    @Transactional
    fun purgeCompletedTasks(
        request: PurgeCompletedTasksRequest,
        excludedProcessInstanceIds: Collection<Long> = emptyList()
    ): MaintenanceCleanupSummary {
        val batchSize = (request.batchSize ?: DEFAULT_PURGE_BATCH_SIZE)
            .coerceIn(1, MAX_PURGE_BATCH_SIZE)
        val taskIds = if (excludedProcessInstanceIds.isEmpty()) {
            taskRepository.findCompletedRetentionCandidateIds(
                status = TaskStatus.COMPLETED,
                before = request.completedBefore,
                pageable = PageRequest.of(0, batchSize)
            )
        } else {
            taskRepository.findCompletedRetentionCandidateIdsExcludingInstances(
                status = TaskStatus.COMPLETED,
                before = request.completedBefore,
                excludedProcessInstanceIds = excludedProcessInstanceIds,
                pageable = PageRequest.of(0, batchSize)
            )
        }

        return cleanupCompletedTasks(taskIds, dryRun = request.dryRun)
    }

    fun previewDeleteProcessDefinition(processDefinitionId: Long): MaintenanceCleanupSummary =
        deleteProcessDefinition(processDefinitionId, dryRun = true)

    @Transactional
    fun deleteProcessDefinition(processDefinitionId: Long, dryRun: Boolean): MaintenanceCleanupSummary {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        val instanceIds = processInstanceRepository.findIdsByProcessDefinitionId(processDefinitionId)
        val cleanup = cleanupInstances(instanceIds, dryRun)

        if (!dryRun) {
            processDefinitionRepository.delete(definition)
        }

        return cleanup.copy(
            dryRun = dryRun,
            processDefinitionsDeleted = 1
        )
    }

    private fun cleanupInstances(instanceIds: List<Long>, dryRun: Boolean): MaintenanceCleanupSummary {
        val uniqueInstanceIds = instanceIds.distinct()
        var tasksDeleted = 0
        var processVariablesDeleted = 0
        var taskVariablesDeleted = 0
        var documentsDeleted = 0
        var messageSubscriptionsDeleted = 0
        var workerRequestsDeleted = 0
        var codeTaskExecutionsDeleted = 0
        var incidentsDeleted = 0
        var incidentEventsDeleted = 0
        var timelineEventsDeleted = 0
        var callActivityMappingsDeleted = 0

        uniqueInstanceIds.forEach { instanceId ->
            val taskIds = taskRepository.findIdsByProcessInstanceId(instanceId)
            val incidentIds = incidentRepository.findIdsByProcessInstanceId(instanceId)

            tasksDeleted += taskRepository.countByProcessInstanceId(instanceId).toInt()
            taskVariablesDeleted += if (taskIds.isEmpty()) 0 else taskVariableRepository.countByTaskIdIn(taskIds).toInt()
            processVariablesDeleted += processVariableRepository.countByProcessInstanceId(instanceId).toInt()
            documentsDeleted += if (taskIds.isEmpty()) {
                documentRepository.countByProcessInstanceId(instanceId).toInt()
            } else {
                documentRepository.countForProcessInstanceCleanup(instanceId, taskIds).toInt()
            }
            messageSubscriptionsDeleted += messageSubscriptionRepository.countByProcessInstanceId(instanceId).toInt()
            workerRequestsDeleted += workerRequestRepository.countByProcessInstanceId(instanceId).toInt()
            codeTaskExecutionsDeleted += codeTaskExecutionAuditRepository.countByInstanceId(instanceId).toInt()
            incidentsDeleted += incidentRepository.countByProcessInstanceId(instanceId).toInt()
            incidentEventsDeleted += if (incidentIds.isEmpty()) 0 else incidentEventRepository.countByIncidentIdIn(incidentIds).toInt()
            timelineEventsDeleted += timelineRepository.countByProcessInstanceId(instanceId).toInt()
            callActivityMappingsDeleted += (
                callActivityMappingRepository.countByParentInstanceId(instanceId) +
                    callActivityMappingRepository.countByChildInstanceId(instanceId)
                ).toInt()

            if (!dryRun) {
                if (taskIds.isNotEmpty()) {
                    documentRepository.deleteByTaskIdIn(taskIds)
                    taskVariableRepository.deleteByTaskIdIn(taskIds)
                }

                if (incidentIds.isNotEmpty()) {
                    incidentEventRepository.deleteByIncidentIdIn(incidentIds)
                }
                incidentRepository.deleteByProcessInstanceId(instanceId)
                timelineRepository.deleteByProcessInstanceId(instanceId)
                callActivityMappingRepository.deleteByParentInstanceId(instanceId)
                callActivityMappingRepository.deleteByChildInstanceId(instanceId)
                documentRepository.deleteByProcessInstanceId(instanceId)
                messageSubscriptionRepository.deleteByProcessInstanceId(instanceId)
                workerRequestRepository.deleteByProcessInstanceId(instanceId)
                codeTaskExecutionAuditRepository.deleteByInstanceId(instanceId)
                taskRepository.deleteByProcessInstanceId(instanceId)
                processVariableRepository.deleteByProcessInstanceId(instanceId)
                processInstanceRepository.deleteById(instanceId)
            }
        }

        return MaintenanceCleanupSummary(
            dryRun = dryRun,
            processInstancesDeleted = uniqueInstanceIds.size,
            tasksDeleted = tasksDeleted,
            processVariablesDeleted = processVariablesDeleted,
            taskVariablesDeleted = taskVariablesDeleted,
            documentsDeleted = documentsDeleted,
            messageSubscriptionsDeleted = messageSubscriptionsDeleted,
            workerRequestsDeleted = workerRequestsDeleted,
            codeTaskExecutionsDeleted = codeTaskExecutionsDeleted,
            incidentsDeleted = incidentsDeleted,
            incidentEventsDeleted = incidentEventsDeleted,
            timelineEventsDeleted = timelineEventsDeleted,
            callActivityMappingsDeleted = callActivityMappingsDeleted,
            candidateInstanceIds = uniqueInstanceIds
        )
    }

    private fun cleanupCompletedTasks(taskIds: List<Long>, dryRun: Boolean): MaintenanceCleanupSummary {
        val uniqueTaskIds = taskIds.distinct()
        val taskVariablesDeleted = if (uniqueTaskIds.isEmpty()) 0 else taskVariableRepository.countByTaskIdIn(uniqueTaskIds).toInt()
        val documentsDeleted = if (uniqueTaskIds.isEmpty()) 0 else documentRepository.countByTaskIdIn(uniqueTaskIds).toInt()

        if (!dryRun && uniqueTaskIds.isNotEmpty()) {
            documentRepository.deleteByTaskIdIn(uniqueTaskIds)
            taskVariableRepository.deleteByTaskIdIn(uniqueTaskIds)
            taskRepository.deleteByIdIn(uniqueTaskIds)
        }

        return MaintenanceCleanupSummary(
            dryRun = dryRun,
            tasksDeleted = uniqueTaskIds.size,
            taskVariablesDeleted = taskVariablesDeleted,
            documentsDeleted = documentsDeleted,
            candidateTaskIds = uniqueTaskIds
        )
    }
}
