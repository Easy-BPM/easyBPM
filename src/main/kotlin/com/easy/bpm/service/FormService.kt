package com.easy.bpm.service

import com.easy.bpm.model.form.Form
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

@Service
class FormService(
    private val formRepository: FormDefinitionRepository
) {

    // Corrigir o Form ID - to use Form Name
    // Bind Variables into the form
    // 

    fun deploy(name: String, schema: JsonNode): Form {
        val latestVersion = formRepository.findTopByNameOrderByVersionDesc(name)?.version ?: 0
        val newForm = Form(
            name = name,
            schema = schema,
            version = latestVersion + 1
        )
        return formRepository.save(newForm)
    }

    fun getLatestVersionByName(name: String): Form? {
        return formRepository.findTopByNameOrderByVersionDesc(name)
    }

    fun getById(id: Long): Form? {
        return formRepository.findById(id).orElse(null)
    }

    fun getAllVersionsByName(name: String): List<Form> {
        return formRepository.findByName(name)
    }
}
