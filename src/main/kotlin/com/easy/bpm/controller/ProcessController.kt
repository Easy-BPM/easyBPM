package com.easy.bpm.controller

import com.easy.bpm.controller.data.AssignProcessVariablesRequest
import com.easy.bpm.controller.data.CallActivityMappingResponse
import com.easy.bpm.controller.data.DeployProcessRequest
import com.easy.bpm.controller.data.MoveNodeRequest
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.service.ProcessService
import com.easy.bpm.util.ParseXMLToJsonFormat.convertXmlToInternalJson
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
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
    fun deploy(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Process definition payload in internal JSON format",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(type = "object"),
                    examples = [
                        ExampleObject(
                            name = "approval-process",
                            summary = "Simple approval process",
                            value = """
                            {
                              "key": "approval-process",
                              "description": "Expense approval workflow",
                              "nodes": [
                                { "id": "start", "type": "START_EVENT", "next": ["managerReview"] },
                                { "id": "managerReview", "type": "USER_TASK", "assignee": "manager", "next": ["end"] },
                                { "id": "end", "type": "END_EVENT" }
                              ]
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody request: JsonNode
    ): ProcessDefinition {
        return processService.deployProcess(request)
    }

    @PostMapping("/{processId}/start")
    @Operation(summary = "Start a process instance", description = "Create and start a new instance of a process definition by processId")
    fun startInstance(@PathVariable processId: String): ProcessInstance {
        return processService.startProcessInstance(processId)
    }

    @GetMapping("/instances")
    @Operation(summary = "Get process instances", description = "Retrieve all process instances with pagination")
    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> {
        return processService.getProcessInstances(pageable)
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "Get process instance by ID", description = "Retrieve a specific process instance by its ID")
    fun getProcessInstanceById(@PathVariable id: Long): ResponseEntity<ProcessInstance> {
        return processService.getProcessInstanceById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/instances/{id}/children")
    @Operation(summary = "Get child process instances", description = "Retrieve all subprocess instances spawned by the given parent instance")
    fun getChildInstances(@PathVariable id: Long): ResponseEntity<List<ProcessInstance>> {
        return try {
            ResponseEntity.ok(processService.getChildProcessInstances(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/instances/{id}/parent")
    @Operation(summary = "Get parent process instance", description = "Retrieve the parent instance for a subprocess instance")
    fun getParentInstance(@PathVariable id: Long): ResponseEntity<ProcessInstance> {
        return processService.getParentProcessInstance(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/instances/{parentId}/children/{childId}/mapping")
    @Operation(summary = "Get call activity mapping", description = "Retrieve input/output variable mapping for a parent-child call activity relationship")
    fun getCallActivityMapping(
        @PathVariable parentId: Long,
        @PathVariable childId: Long
    ): ResponseEntity<CallActivityMappingResponse> {
        val mapping = processService.getCallActivityMapping(parentId, childId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            CallActivityMappingResponse(
                id = mapping.id,
                parentInstanceId = mapping.parentInstanceId,
                childInstanceId = mapping.childInstanceId,
                callActivityNodeId = mapping.callActivityNodeId,
                inputMappings = mapping.getInputMappingsAsMap(),
                outputMappings = mapping.getOutputMappingsAsMap(),
                propagateAllVariables = mapping.propagateAllVariables
            )
        )
    }

    @GetMapping("/instances/{id}/variables")
    @Operation(summary = "Get process variables", description = "Retrieve all variables for a process instance")
    fun getProcessVariables(@PathVariable id: Long): ResponseEntity<List<ProcessVariable>> {
        return try {
            ResponseEntity.ok(processService.getProcessVariables(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/instances/{id}/variables")
    @Operation(summary = "Assign process variables", description = "Create or update process variables for a process instance")
    fun assignProcessVariables(
        @PathVariable id: Long,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Variables to create or update for the process instance",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AssignProcessVariablesRequest::class),
                    examples = [
                        ExampleObject(
                            name = "assignment-variables",
                            summary = "Assign primitive and nested variables",
                            value = """
                            {
                              "variables": {
                                "approved": true,
                                "amount": 1250.75,
                                "currency": "EUR",
                                "requester": {
                                  "id": 42,
                                  "name": "Alice"
                                }
                              }
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody request: AssignProcessVariablesRequest
    ): ResponseEntity<List<ProcessVariable>> {
        return try {
            ResponseEntity.ok(processService.assignProcessVariables(id, request.variables))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/instances/{id}/move-node")
    @Operation(summary = "Move process token", description = "Manually move process execution from one node to another")
    fun moveProcessNode(
        @PathVariable id: Long,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Source and target node ids for manual token move",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MoveNodeRequest::class),
                    examples = [
                        ExampleObject(
                            name = "skip-manual-review",
                            summary = "Move token between nodes",
                            value = """
                            {
                              "fromNode": "manual-review",
                              "toNode": "approve-request",
                              "reason": "SLA escalation approved by supervisor"
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody request: MoveNodeRequest
    ): ResponseEntity<ProcessInstance> {
        return try {
            ResponseEntity.ok(processService.moveProcessNode(id, request.fromNode, request.toNode))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/instances/{id}/stop")
    @Operation(summary = "Stop process instance", description = "Cancel an active process instance and stop further execution")
    fun stopProcessInstance(@PathVariable id: Long): ResponseEntity<ProcessInstance> {
        return try {
            ResponseEntity.ok(processService.stopProcessInstance(id))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/instances/{id}")
    @Operation(summary = "Delete process instance", description = "Hard delete a process instance and related runtime data")
    fun deleteProcessInstance(@PathVariable id: Long): ResponseEntity<Void> {
        return try {
            processService.deleteProcessInstance(id)
            ResponseEntity.noContent().build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/instances/{id}/ad-hoc/{nodeId}/context")
    @Operation(summary = "Get ad-hoc subprocess context", description = "Returns ad-hoc runtime state, eligible activities, variables, and audit trail for AI/human orchestration")
    fun getAdHocContext(
        @PathVariable id: Long,
        @PathVariable nodeId: String
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            ResponseEntity.ok(processService.getAdHocContext(id, nodeId))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/instances/{id}/ad-hoc/{nodeId}/decisions")
    @Operation(summary = "Submit ad-hoc orchestration decision", description = "Submit AI/human decision for ad-hoc subprocess activity routing, recommendation, escalation, skip, or repeat")
    fun submitAdHocDecision(
        @PathVariable id: Long,
        @PathVariable nodeId: String,
        @RequestBody decision: Map<String, Any?>
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            ResponseEntity.ok(processService.submitAdHocDecision(id, nodeId, decision))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Invalid ad-hoc decision")))
        }
    }

    @GetMapping
    @Operation(summary = "Get latest process definitions", description = "Retrieve the latest versions of all process definitions")
    fun getLatestProcesses(pageable: Pageable): Page<ProcessDefinition> {
        return processService.getLatestProcessDefinitions(pageable)
    }

    @GetMapping("/definitions/{id}")
    @Operation(summary = "Get process definition by ID", description = "Retrieve a specific deployed process definition version by its ID")
    fun getProcessDefinitionById(@PathVariable id: Long): ResponseEntity<ProcessDefinition> {
        return processService.getProcessDefinitionById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a message", description = "Send a message to trigger message-based events in running process instances")
    fun sendMessage(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Message event payload with correlation key and optional variables",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(type = "object"),
                    examples = [
                        ExampleObject(
                            name = "invoice-received",
                            summary = "Correlate message to waiting instance",
                            value = """
                            {
                              "messageName": "invoice-received",
                              "correlationKey": "ORDER-12345",
                              "variables": {
                                "invoiceId": "INV-7788",
                                "amount": 540.0,
                                "receivedAt": "2026-04-15T14:30:00Z"
                              }
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody request: Map<String, Any>
    ): Map<String, Any> {
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
