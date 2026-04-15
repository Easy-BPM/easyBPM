package com.easy.bpm.controller.data

data class MoveNodeRequest(
    val fromNode: String,
    val toNode: String,
    val reason: String? = null
)
