package com.easy.bpm.decision

import com.easy.bpm.enum.NodeType
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.incident.*
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.incident.IncidentEventRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.process.ProcessExecutionEngine
import com.easy.bpm.service.incident.IncidentService
import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@Service
class DecisionIncidentRetryService(
    private val incidents: IncidentRepository,
    private val events: IncidentEventRepository,
    private val instances: ProcessInstanceRepository,
    private val engine: ProcessExecutionEngine,
    private val incidentService: IncidentService,
    private val mapper: ObjectMapper,
    private val entityManager: EntityManager
) {
    @Transactional
    fun retry(id: Long, actor: String): Incident {
        val incident = incidents.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found") }
        val instance = instances.findByIdForUpdate(incident.processInstanceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process instance not found")
        // Refresh after taking the instance lock: a concurrent retry may have resolved this incident.
        entityManager.refresh(incident)
        if (incident.source != IncidentSource.DECISION_TASK || incident.status == IncidentStatus.RESOLVED || instance.status != ProcessStatus.FAILED || instance.errorNodeId != incident.nodeId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Decision incident is no longer retryable")
        }
        val nodeId = incident.nodeId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Incident has no task")
        val definition = BpmnXmlCodec.parseDefinition(instance.processDefinition.definitionJson, mapper)
        val node = definition.path("nodes").firstOrNull { it.path("id").asText() == nodeId }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Decision task no longer exists")
        if (NodeType.fromString(node.path("type").asText()) != NodeType.BusinessRuleTask) throw ResponseStatusException(HttpStatus.CONFLICT, "Incident is not a decision task")
        events.save(IncidentEvent(incidentId = id, eventType = IncidentEventType.RETRY_REQUESTED, message = "Pinned decision task retry requested.", actor = actor))
        instance.status = ProcessStatus.ACTIVE
        instance.currentNode = listOf(nodeId)
        instance.errorNodeId = null
        instance.errorMessage = null
        instance.updatedAt = LocalDateTime.now()
        instances.save(instance)
        // Do not hold an incident-row lock here: ordinary failure recording uses REQUIRES_NEW.
        engine.executeNodes(listOf(nodeId), instance, definition)
        entityManager.refresh(incident)
        return if (instance.status == ProcessStatus.FAILED && instance.errorNodeId == nodeId) incident else {
            incidentService.resolveIncident(id, actor, "Decision task completed using its pinned version.", IncidentResolutionAction.RETRIED_SUCCESSFULLY)
        }
    }
}
