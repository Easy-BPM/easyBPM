package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployProcessRequest
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.ProcessService
import com.easy.bpm.util.ParseXMLToJsonFormat.convertXmlToInternalJson
import com.fasterxml.jackson.databind.ObjectMapper
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
class ProcessController(
        private val processService: ProcessService,
        private val objectMapper: ObjectMapper
) {


    @PostMapping
    fun deploy(@RequestBody request: DeployProcessRequest): ProcessDefinition {
        return processService.deployProcess(request.name, request.definitionJson)
    }

    @PostMapping("/{processDefinitionId}/start")
    fun startInstance(@PathVariable processDefinitionId: Long): ProcessInstance {
        return processService.startProcessInstance(processDefinitionId)
    }

    @GetMapping("/instances")
    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> {
        return processService.getProcessInstances(pageable)
    }

    @GetMapping
    fun getLatestProcesses(pageable: Pageable): Page<ProcessDefinition> {
        return processService.getLatestProcessDefinitions(pageable)
    }

}
