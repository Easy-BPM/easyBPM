package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployProcessRequest
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.ProcessService
import com.easy.bpm.util.ParseXMLToJsonFormat.convertXmlToInternalJson
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*


// Bugs
// Task variables has not being created
// Variables are saving with the incorrect value
// It is not updating variables in case of the same name
// It is not ending the procecss when the workflow is done

/*

SELECT * FROM PROCESS_DEFINITION
SELECT * FROM TASK
SELECT * FROM TASK_VARIABLE
SELECT * FROM process_variable
SELECT * FROM process_INSTANCE

 */

@RestController
@RequestMapping("/processes")
@Tag(name = "Processes", description = "Process definition and instance management")
class ProcessController(
        private val processService: ProcessService,
        private val objectMapper: ObjectMapper
) {


    @PostMapping
    @Operation(summary = "Deploy a process definition", description = "Upload and deploy a new BPMN process definition")
    fun deploy(@RequestBody request: JsonNode): ProcessDefinition {
        return processService.deployProcess(request)
    }

    @PostMapping("/{processDefinitionId}/start")
    @Operation(summary = "Start a process instance", description = "Create and start a new instance of a process definition")
    fun startInstance(@PathVariable processDefinitionId: Long): ProcessInstance {
        return processService.startProcessInstance(processDefinitionId)
    }

    @GetMapping("/instances")
    @Operation(summary = "Get process instances", description = "Retrieve all process instances with pagination")
    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> {
        return processService.getProcessInstances(pageable)
    }

    @GetMapping
    @Operation(summary = "Get latest process definitions", description = "Retrieve the latest versions of all process definitions")
    fun getLatestProcesses(pageable: Pageable): Page<ProcessDefinition> {
        return processService.getLatestProcessDefinitions(pageable)
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a message", description = "Send a message to trigger message-based events in running process instances")
    fun sendMessage(@RequestBody request: Map<String, Any>): Map<String, Any> {
        val messageName = request["messageName"] as? String
            ?: throw IllegalArgumentException("Missing messageName")
        val correlationKey = request["correlationKey"] as? String
            ?: throw IllegalArgumentException("Missing correlationKey")
        @Suppress("UNCHECKED_CAST")
        val variables = request["variables"] as? Map<String, Any>

        processService.handleMessageReceived(messageName, correlationKey, variables)

        return mapOf(
            "status" to "success",
            "message" to "Message received and process resumed",
            "messageName" to messageName,
            "correlationKey" to correlationKey
        )
    }

}
