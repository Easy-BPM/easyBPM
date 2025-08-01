package com.easy.bpm.repository.form

import com.easy.bpm.model.form.Form
import org.springframework.data.jpa.repository.JpaRepository

interface FormDefinitionRepository : JpaRepository<Form, Long> {
    fun findByName(name: String): List<Form>
    fun findTopByNameOrderByVersionDesc(name: String): Form?
}