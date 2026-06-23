package com.easy.bpm.controller.data

import com.easy.bpm.model.incident.IncidentResolutionAction

data class IncidentResolutionRequest(
    val resolvedBy: String? = null,
    val resolutionNote: String? = null,
    val resolutionAction: IncidentResolutionAction? = null
)

data class IncidentAcknowledgementRequest(
    val acknowledgedBy: String? = null
)

data class IncidentRetryRequest(
    val requestedBy: String? = null
)

data class IncidentSummaryResponse(
    val openIncidents: Long,
    val criticalIncidents: Long,
    val acknowledgedIncidents: Long,
    val incidentsCreatedToday: Long
)
