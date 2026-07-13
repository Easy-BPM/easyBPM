package com.easy.bpm.repository.form

import com.easy.bpm.model.form.Form
import org.springframework.data.jpa.repository.JpaRepository

interface FormDefinitionRepository : JpaRepository<Form, Long> {
    fun findByTenantIdAndFormIdOrderByVersionAsc(tenantId: String, formId: String): List<Form>
    fun findTopByTenantIdAndFormIdOrderByVersionDesc(tenantId: String, formId: String): Form?
    fun findByTenantIdAndName(tenantId: String, name: String): List<Form>
    fun findTopByTenantIdAndNameOrderByVersionDesc(tenantId: String, name: String): Form?
}
