package com.easy.bpm.service.process

import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.incident.IncidentService
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProcessFailureHandler(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val incidentService: IncidentService,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun failInstance(
        instance: ProcessInstance,
        nodeId: String,
        errorMessage: String,
        incidentSource: IncidentSource = IncidentSource.PROCESS_ENGINE,
        externalReferenceId: String? = null,
        createIncident: Boolean = true
    ) {
        instance.status = ProcessStatus.FAILED
        instance.currentNode = emptyList()
        instance.errorNodeId = nodeId
        instance.errorMessage = errorMessage.take(4000)
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        if (createIncident) {
            incidentService.createIncident(
                processInstanceId = instance.id,
                nodeId = nodeId,
                source = incidentSource,
                message = errorMessage,
                technicalDetails = "Process instance ${instance.id} failed at node '$nodeId'",
                externalReferenceId = externalReferenceId
            )
        }
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.PROCESS_FAILED,
            message = "Process instance failed at node '$nodeId'.",
            details = errorMessage
        )
    }

    fun incidentSourceForNode(nodeType: NodeType): IncidentSource =
        when (nodeType) {
            NodeType.CodeTask -> IncidentSource.CODE_TASK
            NodeType.AiTask -> IncidentSource.AI_TASK
            NodeType.MessageEvent,
            NodeType.MessageStartEvent,
            NodeType.MessageIntermediateCatchEvent,
            NodeType.MessageIntermediateThrowEvent -> IncidentSource.MESSAGE
            else -> IncidentSource.PROCESS_ENGINE
        }
}
