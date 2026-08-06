package com.easy.bpm.service

import com.easy.bpm.controller.data.MaintenanceCleanupSummary
import com.easy.bpm.controller.data.PurgeCompletedInstancesRequest
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.repository.CodeTaskExecutionAuditRepository
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
    fun previewPurgeCompletedInstances(request: PurgeCompletedInstancesRequest): MaintenanceCleanupSummary =
        purgeCompletedInstances(request.copy(dryRun = true))

    @Transactional
    fun purgeCompletedInstances(request: PurgeCompletedInstancesRequest): MaintenanceCleanupSummary {
        val candidates = processInstanceRepository.findPurgeCandidates(
            status = ProcessStatus.COMPLETED,
            before = request.completedBefore,
            processDefinitionId = request.processDefinitionId,
            processKey = request.processKey?.takeIf { it.isNotBlank() }
        )

        return cleanupInstances(candidates.map { it.id }, dryRun = request.dryRun)
    }

    fun previewDeleteProcessDefinition(processDefinitionId: Long): MaintenanceCleanupSummary =
        deleteProcessDefinition(processDefinitionId, dryRun = true)

    @Transactional
    fun deleteProcessDefinition(processDefinitionId: Long, dryRun: Boolean): MaintenanceCleanupSummary {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        val instanceIds = processInstanceRepository.findByProcessDefinitionId(processDefinitionId).map { it.id }
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
            val tasks = taskRepository.findByProcessInstanceId(instanceId)
            tasksDeleted += tasks.size
            val documentIds = mutableSetOf<java.util.UUID>()
            tasks.forEach { task ->
                val taskVariables = taskVariableRepository.findByTaskId(task.id)
                taskVariablesDeleted += taskVariables.size
                documentIds.addAll(documentRepository.findByTaskId(task.id).mapNotNull { it.id })
            }

            processVariablesDeleted += processVariableRepository.findByProcessInstanceId(instanceId).size
            documentIds.addAll(documentRepository.findByProcessInstanceId(instanceId).mapNotNull { it.id })
            documentsDeleted += documentIds.size
            messageSubscriptionsDeleted += messageSubscriptionRepository.findByProcessInstanceId(instanceId).size
            workerRequestsDeleted += workerRequestRepository.findByProcessInstanceId(instanceId).size
            codeTaskExecutionsDeleted += codeTaskExecutionAuditRepository.findByInstanceId(instanceId).size

            val incidents = incidentRepository.findByProcessInstanceId(instanceId)
            incidentsDeleted += incidents.size
            incidents.forEach { incident ->
                incidentEventsDeleted += incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(incident.id).size
            }
            timelineEventsDeleted += timelineRepository.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instanceId).size

            val childMapping = callActivityMappingRepository.findByChildInstanceId(instanceId)
            val parentMappings = callActivityMappingRepository.findByParentInstanceId(instanceId)
            callActivityMappingsDeleted += parentMappings.size + if (childMapping != null) 1 else 0

            if (!dryRun) {
                tasks.forEach { task ->
                    documentRepository.deleteByTaskId(task.id)
                    taskVariableRepository.deleteByTaskId(task.id)
                }

                incidents.forEach { incident ->
                    incidentEventRepository.deleteByIncidentId(incident.id)
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
}
