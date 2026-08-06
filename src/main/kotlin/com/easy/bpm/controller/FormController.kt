package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployFormRequest
import com.easy.bpm.model.form.Form
import com.easy.bpm.service.form.FormService
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/forms")
@Tag(name = "Forms", description = "Form deployment and retrieval")
class FormController(
    private val formService: FormService
) {

    @PostMapping
    @Operation(summary = "Deploy a form", description = "Create and deploy a new form definition")
    fun deployForm(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Form deployment payload containing stable formId, display name, and JSON schema",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = DeployFormRequest::class),
                    examples = [
                        ExampleObject(
                            name = "expense-form",
                            summary = "Deploy expense request form",
                            value = """
                            {
                                                            "formId": "expenseRequest",
                              "name": "expense-request",
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "employeeId": { "type": "string" },
                                  "amount": { "type": "number" },
                                  "description": { "type": "string" }
                                },
                                "required": ["employeeId", "amount"]
                              }
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody request: DeployFormRequest
    ): Form {
        return formService.deploy(request.formId, request.name, request.schema)
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest form version", description = "Retrieve the latest version of a form by formId. The name parameter is also supported for lookup by form name.")
    fun getLatest(
        @RequestParam(required = false) formId: String?,
        @RequestParam(required = false) name: String?
    ): Form? {
        return when {
            !formId.isNullOrBlank() -> formService.getLatestVersionByFormId(formId)
            !name.isNullOrBlank() -> formService.getLatestVersionByName(name)
            else -> throw IllegalArgumentException("Either formId or name query parameter is required")
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get form by ID", description = "Retrieve a specific form by its ID")
    fun getById(@PathVariable id: Long): Form? {
        return formService.getById(id)
    }

    @GetMapping
    @Operation(summary = "Get all form versions", description = "Retrieve all versions of a form by formId. The name parameter is also supported for lookup by form name.")
    fun getAllVersions(
        @RequestParam(required = false) formId: String?,
        @RequestParam(required = false) name: String?
    ): List<Form> {
        return when {
            !formId.isNullOrBlank() -> formService.getAllVersionsByFormId(formId)
            !name.isNullOrBlank() -> formService.getAllVersionsByName(name)
            else -> throw IllegalArgumentException("Either formId or name query parameter is required")
        }
    }
}

