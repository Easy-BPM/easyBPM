package com.easy.bpm.controller.data

import io.swagger.v3.oas.annotations.media.Schema

data class AssignProcessVariablesRequest(
    @field:Schema(
        description = "Map of variable names to values. Supports primitive and nested JSON values.",
        example = "{\"approved\":true,\"amount\":1250.75,\"requester\":{\"id\":42,\"name\":\"Alice\"}}"
    )
    val variables: Map<String, Any?> = emptyMap()
)
