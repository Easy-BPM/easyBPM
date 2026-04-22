package com.easy.bpm.controller.data

import com.fasterxml.jackson.databind.JsonNode

data class DeployFormRequest(
    val formId: String,
    val name: String,
    val schema: JsonNode
)
