package com.easy.bpm.controller

import com.easy.bpm.model.agent.AgentProcessDefinition
import com.easy.bpm.service.AgentProcessService
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/agent-processes")
@Tag(name = "Agent Processes", description = "Agentic process definition management")
class AgentProcessController(
    private val agentProcessService: AgentProcessService
) {
    @PostMapping
    @Operation(summary = "Deploy an agent process definition")
    fun deploy(@RequestBody request: JsonNode): AgentProcessDefinition =
        agentProcessService.deploy(request)

    @GetMapping
    @Operation(summary = "Get latest agent process definitions")
    fun listLatest(): List<AgentProcessDefinition> =
        agentProcessService.getLatestDefinitions()

    @GetMapping("/{key}")
    @Operation(summary = "Get latest agent process definition by key")
    fun getLatest(@PathVariable key: String): ResponseEntity<AgentProcessDefinition> =
        agentProcessService.getLatestDefinition(key)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{key}/versions")
    @Operation(summary = "Get all versions for an agent process key")
    fun getVersions(@PathVariable key: String): List<AgentProcessDefinition> =
        agentProcessService.getVersions(key)
}
