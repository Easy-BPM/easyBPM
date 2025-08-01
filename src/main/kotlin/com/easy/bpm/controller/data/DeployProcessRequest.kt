package com.easy.bpm.controller.data

import com.fasterxml.jackson.databind.JsonNode

data class DeployProcessRequest(
    val name: String,
    val definitionJson: JsonNode
)