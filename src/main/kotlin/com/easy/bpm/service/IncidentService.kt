package com.easy.bpm.service

import com.easy.bpm.controller.data.IncidentSummaryResponse
import com.easy.bpm.model.incident.Incident
import com.easy.bpm.model.incident.IncidentEvent
import com.easy.bpm.model.incident.IncidentEventType
import com.easy.bpm.model.incident.IncidentResolutionAction
import com.easy.bpm.model.incident.IncidentSeverity
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.incident.IncidentStatus
import com.easy.bpm.model.worker.WorkerRequest
import com.easy.bpm.model.worker.WorkerRequestStatus
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.incident.IncidentEventRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.easy.bpm.messaging.RabbitPublisher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val incidentEventRepository: IncidentEventRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val workerRequestRepository: WorkerRequestRepository,
    private val rabbitPublisher: RabbitPublisher,
    private val objectMapper: ObjectMapper,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun getIncidents(
        status: IncidentStatus?,
        source: IncidentSource?,
        processInstanceId: Long?,
        pageable: Pageable
    ): Page<Incident> {
        val specification = Specification<Incident> { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            status?.let { predicates.add(criteriaBuilder.equal(root.get<IncidentStatus>("status"), it)) }
            source?.let { predicates.add(criteriaBuilder.equal(root.get<IncidentSource>("source"), it)) }
            processInstanceId?.let { predicates.add(criteriaBuilder.equal(root.get<Long>("processInstanceId"), it)) }

            criteriaBuilder.and(*predicates.toTypedArray())
        }

        return incidentRepository.findAll(specification, pageable)
    }

    fun getIncident(id: Long): Incident? =
        incidentRepository.findById(id).orElse(null)

    fun getIncidentsForProcessInstance(processInstanceId: Long): List<Incident> =
        incidentRepository.findByProcessInstanceIdOrderByCreatedAtDesc(processInstanceId)

    fun getIncidentEvents(id: Long): List<IncidentEvent> {
        if (!incidentRepository.existsById(id)) {
            throw IllegalArgumentException("Incident not found")
        }
        return incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(id)
    }

    fun getSummary(): IncidentSummaryResponse {
        val today = LocalDate.now().atStartOfDay()
        return IncidentSummaryResponse(
            openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN),
            criticalIncidents = incidentRepository.countBySeverityAndStatusNot(
                IncidentSeverity.CRITICAL,
                IncidentStatus.RESOLVED
            ),
            acknowledgedIncidents = incidentRepository.countByStatus(IncidentStatus.ACKNOWLEDGED),
            incidentsCreatedToday = incidentRepository.countCreatedSince(today)
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createIncident(
        processInstanceId: Long,
        nodeId: String?,
        source: IncidentSource,
        message: String,
        technicalDetails: String? = null,
        severity: IncidentSeverity = IncidentSeverity.HIGH,
        externalReferenceId: String? = null
    ): Incident {
        val existingIncident = incidentRepository
            .findTopByProcessInstanceIdAndNodeIdAndSourceAndStatusInOrderByCreatedAtDesc(
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                source = source,
                statuses = listOf(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED)
            )

        if (existingIncident != null) {
            val now = LocalDateTime.now()
            existingIncident.status = IncidentStatus.OPEN
            existingIncident.severity = severity
            existingIncident.message = message.take(4000)
            existingIncident.technicalDetails = technicalDetails?.take(8000)
            existingIncident.externalReferenceId = externalReferenceId ?: existingIncident.externalReferenceId
            existingIncident.occurrenceCount += 1
            existingIncident.lastOccurredAt = now
            existingIncident.updatedAt = now
            val savedIncident = incidentRepository.save(existingIncident)
            recordEvent(
                incidentId = savedIncident.id,
                eventType = IncidentEventType.OCCURRED_AGAIN,
                message = "Incident occurred again. Occurrence count: ${savedIncident.occurrenceCount}."
            )
            return savedIncident
        }

        val savedIncident = incidentRepository.save(
            Incident(
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                source = source,
                severity = severity,
                message = message.take(4000),
                technicalDetails = technicalDetails?.take(8000),
                externalReferenceId = externalReferenceId
            )
        )
        recordEvent(
            incidentId = savedIncident.id,
            eventType = IncidentEventType.CREATED,
            message = "Incident created from ${savedIncident.source}."
        )
        timelineService.record(
            processInstanceId = savedIncident.processInstanceId,
            nodeId = savedIncident.nodeId,
            eventType = ProcessInstanceEventType.INCIDENT_CREATED,
            message = "Incident #${savedIncident.id} created from ${savedIncident.source}.",
            details = savedIncident.message
        )
        return savedIncident
    }

    @Transactional
    fun retryWorkerIncident(id: Long, requestedBy: String?): Incident {
        val incident = incidentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Incident not found") }

        if (incident.source != IncidentSource.WORKER) {
            throw IllegalStateException("Only WORKER incidents can be retried")
        }

        val nodeId = incident.nodeId
            ?: throw IllegalStateException("Incident is not linked to a process node")

        val instance = processInstanceRepository.findById(incident.processInstanceId)
            .orElseThrow { IllegalStateException("Process instance not found") }

        val definition = objectMapper.readTree(instance.processDefinition.definitionJson)
        val node = findNode(definition, nodeId)
        val properties = node.get("properties") ?: node.get("service") ?: node.get("config") ?: objectMapper.createObjectNode()

        val workerRequest = workerRequestRepository.findByProcessInstanceIdAndNodeId(instance.id, nodeId)
            ?: WorkerRequest(
                processInstanceId = instance.id,
                nodeId = nodeId,
                idempotencyKey = "${instance.id}:$nodeId"
            )

        workerRequest.status = WorkerRequestStatus.PENDING
        workerRequest.retryCount = 0
        workerRequest.lastError = null
        workerRequest.lastAttemptAt = null
        workerRequest.completedAt = null
        workerRequestRepository.save(workerRequest)

        instance.status = com.easy.bpm.enum.ProcessStatus.ACTIVE
        instance.currentNode = listOf(nodeId)
        instance.errorNodeId = null
        instance.errorMessage = null
        instance.updatedAt = LocalDateTime.now()
        processInstanceRepository.save(instance)

        rabbitPublisher.publishServiceTaskRequest(instance.id, nodeId, properties)

        val now = LocalDateTime.now()
        incident.status = IncidentStatus.ACKNOWLEDGED
        incident.acknowledgedAt = now
        incident.acknowledgedBy = requestedBy
        incident.resolutionNote = appendNote(incident.resolutionNote, "Retry requested${requestedBy?.let { " by $it" } ?: ""}.")
        incident.updatedAt = now

        val savedIncident = incidentRepository.save(incident)
        recordEvent(
            incidentId = savedIncident.id,
            eventType = IncidentEventType.RETRY_REQUESTED,
            message = "Worker retry requested.",
            actor = requestedBy
        )
        timelineService.record(
            processInstanceId = savedIncident.processInstanceId,
            nodeId = savedIncident.nodeId,
            eventType = ProcessInstanceEventType.INCIDENT_RETRY_REQUESTED,
            message = "Retry requested for incident #${savedIncident.id}.",
            actor = requestedBy
        )
        return savedIncident
    }

    @Transactional
    fun acknowledgeIncident(id: Long, acknowledgedBy: String?): Incident {
        val incident = incidentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Incident not found") }

        if (incident.status == IncidentStatus.RESOLVED) {
            return incident
        }

        val now = LocalDateTime.now()
        incident.status = IncidentStatus.ACKNOWLEDGED
        incident.acknowledgedAt = now
        incident.acknowledgedBy = acknowledgedBy
        incident.updatedAt = now

        val savedIncident = incidentRepository.save(incident)
        recordEvent(
            incidentId = savedIncident.id,
            eventType = IncidentEventType.ACKNOWLEDGED,
            message = "Incident acknowledged.",
            actor = acknowledgedBy
        )
        return savedIncident
    }

    @Transactional
    fun resolveIncident(
        id: Long,
        resolvedBy: String?,
        resolutionNote: String?,
        resolutionAction: IncidentResolutionAction?
    ): Incident {
        val incident = incidentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Incident not found") }

        val now = LocalDateTime.now()
        incident.status = IncidentStatus.RESOLVED
        incident.resolvedAt = now
        incident.resolvedBy = resolvedBy
        incident.resolutionNote = resolutionNote
        incident.resolutionAction = resolutionAction
        incident.updatedAt = now

        val savedIncident = incidentRepository.save(incident)
        recordEvent(
            incidentId = savedIncident.id,
            eventType = IncidentEventType.RESOLVED,
            message = resolutionAction?.let { "Incident resolved with action $it." } ?: "Incident resolved.",
            actor = resolvedBy
        )
        timelineService.record(
            processInstanceId = savedIncident.processInstanceId,
            nodeId = savedIncident.nodeId,
            eventType = ProcessInstanceEventType.INCIDENT_RESOLVED,
            message = "Incident #${savedIncident.id} resolved.",
            actor = resolvedBy,
            details = resolutionAction?.name ?: resolutionNote
        )
        return savedIncident
    }

    @Transactional
    fun reopenIncident(id: Long): Incident {
        val incident = incidentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Incident not found") }

        incident.status = IncidentStatus.OPEN
        incident.resolvedAt = null
        incident.resolvedBy = null
        incident.resolutionNote = null
        incident.resolutionAction = null
        incident.updatedAt = LocalDateTime.now()

        val savedIncident = incidentRepository.save(incident)
        recordEvent(
            incidentId = savedIncident.id,
            eventType = IncidentEventType.REOPENED,
            message = "Incident reopened."
        )
        return savedIncident
    }

    private fun findNode(definition: JsonNode, nodeId: String): JsonNode =
        definition.get("nodes")
            .find { it.get("id").asText() == nodeId }
            ?: throw IllegalStateException("Node '$nodeId' not found")

    private fun appendNote(existing: String?, note: String): String =
        listOfNotNull(existing?.takeIf { it.isNotBlank() }, note).joinToString("\n")

    private fun recordEvent(
        incidentId: Long,
        eventType: IncidentEventType,
        message: String,
        actor: String? = null
    ) {
        incidentEventRepository.save(
            IncidentEvent(
                incidentId = incidentId,
                eventType = eventType,
                message = message,
                actor = actor
            )
        )
    }
}
