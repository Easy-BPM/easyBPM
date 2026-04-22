package com.easy.bpm.controller.data

import io.swagger.v3.oas.annotations.media.Schema

data class MoveNodeRequest(
    @field:Schema(description = "Current node id where the token is located", example = "manual-review")
    val fromNode: String,
    @field:Schema(description = "Target node id where the token should move", example = "approve-request")
    val toNode: String,
    @field:Schema(description = "Business reason for manual intervention", example = "SLA escalation approved by supervisor")
    val reason: String? = null
)

