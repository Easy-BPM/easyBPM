package com.easy.bpm.controller

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.ProcessService
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.web.bind.annotation.*



@RestController
@RequestMapping("/api/processes")
class ProcessController(
        private val processService: ProcessService
) {

    @PostMapping
    fun deploy(@RequestBody request: DeployRequest): ProcessDefinition {
        val jsonString = request.definitionJson.toString()
        return processService.deployProcess(request.name, jsonString)
    }

    @PostMapping("/{processDefinitionId}/start")
    fun startInstance(@PathVariable processDefinitionId: Long): ProcessInstance {
        return processService.startProcessInstance(processDefinitionId)
    }

}
