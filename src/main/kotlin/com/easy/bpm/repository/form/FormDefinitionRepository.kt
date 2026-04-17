package com.easy.bpm.repository.form

import com.easy.bpm.model.form.Form
import org.springframework.data.jpa.repository.JpaRepository

interface FormDefinitionRepository : JpaRepository<Form, Long> {
    fun findByFormIdOrderByVersionAsc(formId: String): List<Form>
    fun findTopByFormIdOrderByVersionDesc(formId: String): Form?
    fun findByName(name: String): List<Form>
    fun findTopByNameOrderByVersionDesc(name: String): Form?
}