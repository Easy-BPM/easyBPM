package com.easy.bpm.controller.data

data class CallActivityMappingResponse(
    val id: Long,
    val parentInstanceId: Long,
    val childInstanceId: Long,
    val callActivityNodeId: String,
    val inputMappings: Map<String, String>,
    val outputMappings: Map<String, String>,
    val propagateAllVariables: Boolean
)

