package com.easy.bpm.service

import com.easy.bpm.model.form.Form
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.easy.bpm.tenant.TenantContext
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

@Service
class FormService(
    private val formRepository: FormDefinitionRepository
) {

    private val formIdPattern = Regex("^[A-Za-z][A-Za-z0-9_-]*$")

    fun deploy(formId: String, name: String, schema: JsonNode): Form {
        val tenantId = TenantContext.getTenant()
        val normalizedFormId = normalizeFormId(formId)
        val normalizedName = normalizeName(name)
        val latestVersion = formRepository.findTopByTenantIdAndFormIdOrderByVersionDesc(tenantId, normalizedFormId)?.version ?: 0
        val newForm = Form(
            tenantId = tenantId,
            formId = normalizedFormId,
            name = normalizedName,
            schema = schema,
            version = latestVersion + 1
        )
        return formRepository.save(newForm)
    }

    fun getLatestVersionByFormId(formId: String): Form? {
        return formRepository.findTopByTenantIdAndFormIdOrderByVersionDesc(TenantContext.getTenant(), formId.trim())
    }

    fun getLatestVersionByName(name: String): Form? {
        return formRepository.findTopByTenantIdAndNameOrderByVersionDesc(TenantContext.getTenant(), name.trim())
    }

    fun getById(id: Long): Form? {
        return formRepository.findById(id).orElse(null)?.takeIf { it.tenantId == TenantContext.getTenant() }
    }

    fun getAllVersionsByFormId(formId: String): List<Form> {
        return formRepository.findByTenantIdAndFormIdOrderByVersionAsc(TenantContext.getTenant(), formId.trim())
    }

    fun getAllVersionsByName(name: String): List<Form> {
        return formRepository.findByTenantIdAndName(TenantContext.getTenant(), name.trim())
    }

    private fun normalizeFormId(formId: String): String {
        val normalizedFormId = formId.trim()
        require(normalizedFormId.isNotBlank()) { "Form formId must not be blank" }
        require(formIdPattern.matches(normalizedFormId)) {
            "Form formId must start with a letter and contain only letters, numbers, hyphens, or underscores"
        }
        return normalizedFormId
    }

    private fun normalizeName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Form name must not be blank" }
        return normalizedName
    }
}
