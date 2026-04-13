package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployFormRequest
import com.easy.bpm.model.form.Form
import com.easy.bpm.service.FormService
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
    fun deployForm(@RequestBody request: DeployFormRequest): Form {
        return formService.deploy(request.name, request.schema)
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest form version", description = "Retrieve the latest version of a form by name")
    fun getLatest(@RequestParam name: String): Form? {
        return formService.getLatestVersionByName(name)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get form by ID", description = "Retrieve a specific form by its ID")
    fun getById(@PathVariable id: Long): Form? {
        return formService.getById(id)
    }

    @GetMapping
    @Operation(summary = "Get all form versions", description = "Retrieve all versions of a form by name")
    fun getAllVersions(@RequestParam name: String): List<Form> {
        return formService.getAllVersionsByName(name)
    }
}
