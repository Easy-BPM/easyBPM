package com.easy.bpm.service

import com.easy.bpm.model.form.Form
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

@Service
class FormService(
    private val formRepository: FormDefinitionRepository
) {

    private val formKeyPattern = Regex("^[A-Za-z][A-Za-z0-9_-]*$")

    // Corrigir o Form ID - to use Form Name
    // Bind Variables into the form
    // 

    fun deploy(key: String, name: String, schema: JsonNode): Form {
        val normalizedKey = normalizeKey(key)
        val normalizedName = normalizeName(name)
        val latestVersion = formRepository.findTopByKeyOrderByVersionDesc(normalizedKey)?.version ?: 0
        val newForm = Form(
            key = normalizedKey,
            name = normalizedName,
            schema = schema,
            version = latestVersion + 1
        )
        return formRepository.save(newForm)
    }

    fun getLatestVersionByKey(key: String): Form? {
        return formRepository.findTopByKeyOrderByVersionDesc(key.trim())
    }

    fun getLatestVersionByName(name: String): Form? {
        return formRepository.findTopByNameOrderByVersionDesc(name.trim())
    }

    fun getById(id: Long): Form? {
        return formRepository.findById(id).orElse(null)
    }

    fun getAllVersionsByKey(key: String): List<Form> {
        return formRepository.findByKeyOrderByVersionAsc(key.trim())
    }

    fun getAllVersionsByName(name: String): List<Form> {
        return formRepository.findByName(name.trim())
    }

    private fun normalizeKey(key: String): String {
        val normalizedKey = key.trim()
        require(normalizedKey.isNotBlank()) { "Form key must not be blank" }
        require(formKeyPattern.matches(normalizedKey)) {
            "Form key must start with a letter and contain only letters, numbers, hyphens, or underscores"
        }
        return normalizedKey
    }

    private fun normalizeName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Form name must not be blank" }
        return normalizedName
    }
}
