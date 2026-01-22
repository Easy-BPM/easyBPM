package com.easy.bpm.controller.data

import com.fasterxml.jackson.databind.JsonNode

data class DeployProcessRequest(
    val definitionJson: JsonNode
)