package com.easy.bpm.controller

import com.easy.bpm.controller.data.IncidentAcknowledgementRequest
import com.easy.bpm.controller.data.IncidentResolutionRequest
import com.easy.bpm.model.incident.Incident
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.incident.IncidentStatus
import com.easy.bpm.service.IncidentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class IncidentControllerTest : FunSpec({
    val incidentService = mockk<IncidentService>()
    val incidentController = IncidentController(incidentService)

    beforeEach {
        clearAllMocks()
    }

    test("should return incident when found") {
        val incident = Incident(
            id = 1,
            processInstanceId = 100,
            nodeId = "api-task",
            source = IncidentSource.WORKER,
            message = "API timeout"
        )
        every { incidentService.getIncident(1) } returns incident

        val response = incidentController.getIncident(1)

        response.statusCode.value() shouldBe 200
        response.body shouldBe incident
    }

    test("should return 404 when incident is missing") {
        every { incidentService.getIncident(404) } returns null

        val response = incidentController.getIncident(404)

        response.statusCode.value() shouldBe 404
    }

    test("should acknowledge incident") {
        val incident = Incident(
            id = 1,
            processInstanceId = 100,
            source = IncidentSource.PROCESS_ENGINE,
            message = "Node failed",
            status = IncidentStatus.ACKNOWLEDGED
        )
        every { incidentService.acknowledgeIncident(1, "operator") } returns incident

        val response = incidentController.acknowledgeIncident(1, IncidentAcknowledgementRequest("operator"))

        response.statusCode.value() shouldBe 200
        response.body?.status shouldBe IncidentStatus.ACKNOWLEDGED
        verify { incidentService.acknowledgeIncident(1, "operator") }
    }

    test("should resolve incident") {
        val incident = Incident(
            id = 1,
            processInstanceId = 100,
            source = IncidentSource.PROCESS_ENGINE,
            message = "Node failed",
            status = IncidentStatus.RESOLVED
        )
        every { incidentService.resolveIncident(1, "operator", "Fixed variable", null) } returns incident

        val response = incidentController.resolveIncident(1, IncidentResolutionRequest("operator", "Fixed variable"))

        response.statusCode.value() shouldBe 200
        response.body?.status shouldBe IncidentStatus.RESOLVED
        verify { incidentService.resolveIncident(1, "operator", "Fixed variable", null) }
    }
})
