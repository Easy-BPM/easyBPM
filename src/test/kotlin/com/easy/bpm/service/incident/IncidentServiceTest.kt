package com.easy.bpm.service

import com.easy.bpm.model.incident.Incident
import com.easy.bpm.model.incident.IncidentEvent
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.incident.IncidentStatus
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.incident.IncidentEventRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class IncidentServiceTest : FunSpec({
    val incidentRepository = mockk<IncidentRepository>()
    val incidentEventRepository = mockk<IncidentEventRepository>(relaxed = true)
    val processInstanceRepository = mockk<ProcessInstanceRepository>()
    val workerRequestRepository = mockk<WorkerRequestRepository>()
    val rabbitPublisher = mockk<RabbitPublisher>()
    val objectMapper = ObjectMapper()
    val timelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
    val incidentService = IncidentService(
        incidentRepository,
        incidentEventRepository,
        processInstanceRepository,
        workerRequestRepository,
        rabbitPublisher,
        objectMapper,
        timelineService
    )

    every { incidentEventRepository.save(any<IncidentEvent>()) } answers { firstArg<IncidentEvent>() }

    test("should create an open incident") {
        val incidentSlot = slot<Incident>()
        every {
            incidentRepository.findTopByProcessInstanceIdAndNodeIdAndSourceAndStatusInOrderByCreatedAtDesc(
                42,
                "sync-crm",
                IncidentSource.WORKER,
                listOf(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED)
            )
        } returns null
        every { incidentRepository.save(capture(incidentSlot)) } answers { incidentSlot.captured.copy(id = 10) }

        val incident = incidentService.createIncident(
            processInstanceId = 42,
            nodeId = "sync-crm",
            source = IncidentSource.WORKER,
            message = "CRM timeout",
            externalReferenceId = "worker_request:5"
        )

        incident.id shouldBe 10
        incident.processInstanceId shouldBe 42
        incident.nodeId shouldBe "sync-crm"
        incident.source shouldBe IncidentSource.WORKER
        incident.status shouldBe IncidentStatus.OPEN
        incident.externalReferenceId shouldBe "worker_request:5"
        verify { incidentRepository.save(any()) }
    }

    test("should resolve an incident") {
        val incident = Incident(
            id = 10,
            processInstanceId = 42,
            nodeId = "sync-crm",
            source = IncidentSource.WORKER,
            message = "CRM timeout"
        )

        every { incidentRepository.findById(10) } returns Optional.of(incident)
        every { incidentRepository.save(any()) } answers { firstArg() }

        val resolved = incidentService.resolveIncident(10, "admin", "CRM recovered", null)

        resolved.status shouldBe IncidentStatus.RESOLVED
        resolved.resolvedBy shouldBe "admin"
        resolved.resolutionNote shouldBe "CRM recovered"
        resolved.resolvedAt shouldBe resolved.resolvedAt
        verify { incidentRepository.save(incident) }
    }

    test("should reopen an incident") {
        val incident = Incident(
            id = 10,
            processInstanceId = 42,
            nodeId = "sync-crm",
            source = IncidentSource.WORKER,
            message = "CRM timeout",
            status = IncidentStatus.RESOLVED,
            resolvedBy = "admin",
            resolutionNote = "Done"
        )

        every { incidentRepository.findById(10) } returns Optional.of(incident)
        every { incidentRepository.save(any()) } answers { firstArg() }

        val reopened = incidentService.reopenIncident(10)

        reopened.status shouldBe IncidentStatus.OPEN
        reopened.resolvedBy shouldBe null
        reopened.resolutionNote shouldBe null
        reopened.resolvedAt shouldBe null
    }
})
