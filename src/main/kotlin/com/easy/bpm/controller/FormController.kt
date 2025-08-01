package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployFormRequest
import com.easy.bpm.model.form.Form
import com.easy.bpm.service.FormService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/forms")
class FormController(
    private val formService: FormService
) {

    @PostMapping
    fun deployForm(@RequestBody request: DeployFormRequest): Form {
        return formService.deploy(request.name, request.schema)
    }

    @GetMapping("/latest")
    fun getLatest(@RequestParam name: String): Form? {
        return formService.getLatestVersionByName(name)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): Form? {
        return formService.getById(id)
    }

    @GetMapping
    fun getAllVersions(@RequestParam name: String): List<Form> {
        return formService.getAllVersionsByName(name)
    }
}
