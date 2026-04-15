package com.easy.bpm.controller.data

data class AssignProcessVariablesRequest(
    val variables: Map<String, Any?> = emptyMap()
)
