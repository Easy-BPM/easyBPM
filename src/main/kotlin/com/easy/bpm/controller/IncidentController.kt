package com.easy.bpm.controller

import com.easy.bpm.controller.data.IncidentAcknowledgementRequest
import com.easy.bpm.controller.data.IncidentRetryRequest
import com.easy.bpm.controller.data.IncidentResolutionRequest
import com.easy.bpm.model.incident.Incident
import com.easy.bpm.model.incident.IncidentEvent
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.incident.IncidentStatus
import com.easy.bpm.service.IncidentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/incidents")
@Tag(name = "Incidents", description = "Operational incident tracking and resolution")
class IncidentController(
    private val incidentService: IncidentService
) {
    @GetMapping
    @Operation(summary = "List incidents", description = "Retrieve incidents with optional status, source, and process instance filters")
    fun getIncidents(
        @RequestParam(required = false) status: IncidentStatus?,
        @RequestParam(required = false) source: IncidentSource?,
        @RequestParam(required = false) processInstanceId: Long?,
        pageable: Pageable
    ): Page<Incident> =
        incidentService.getIncidents(status, source, processInstanceId, pageable)

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by ID", description = "Retrieve a single incident")
    fun getIncident(@PathVariable id: Long): ResponseEntity<Incident> =
        incidentService.getIncident(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/process-instances/{processInstanceId}")
    @Operation(summary = "Get incidents for process instance", description = "Retrieve all incidents attached to a process instance")
    fun getIncidentsForProcessInstance(@PathVariable processInstanceId: Long): List<Incident> =
        incidentService.getIncidentsForProcessInstance(processInstanceId)

    @GetMapping("/summary")
    @Operation(summary = "Get incident summary", description = "Retrieve dashboard counts for incident operations")
    fun getSummary() =
        incidentService.getSummary()

    @GetMapping("/{id}/events")
    @Operation(summary = "Get incident timeline", description = "Retrieve lifecycle events for an incident")
    fun getIncidentEvents(@PathVariable id: Long): ResponseEntity<List<IncidentEvent>> =
        try {
            ResponseEntity.ok(incidentService.getIncidentEvents(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge incident", description = "Mark an open incident as acknowledged")
    fun acknowledgeIncident(
        @PathVariable id: Long,
        @RequestBody(required = false) request: IncidentAcknowledgementRequest?
    ): ResponseEntity<Incident> =
        try {
            ResponseEntity.ok(incidentService.acknowledgeIncident(id, request?.acknowledgedBy))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Mark an incident as resolved")
    fun resolveIncident(
        @PathVariable id: Long,
        @RequestBody(required = false) request: IncidentResolutionRequest?
    ): ResponseEntity<Incident> =
        try {
            ResponseEntity.ok(
                incidentService.resolveIncident(
                    id,
                    request?.resolvedBy,
                    request?.resolutionNote,
                    request?.resolutionAction
                )
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reopen incident", description = "Move a resolved incident back to open")
    fun reopenIncident(@PathVariable id: Long): ResponseEntity<Incident> =
        try {
            ResponseEntity.ok(incidentService.reopenIncident(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry incident", description = "Retry a recoverable worker/API incident")
    fun retryIncident(
        @PathVariable id: Long,
        @RequestBody(required = false) request: IncidentRetryRequest?
    ): ResponseEntity<Incident> =
        try {
            ResponseEntity.ok(incidentService.retryWorkerIncident(id, request?.requestedBy))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
}
