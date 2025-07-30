package com.easy.bpm.controller

import com.fasterxml.jackson.databind.JsonNode

data class DeployRequest(
    val name: String,
    val definitionJson: JsonNode
)