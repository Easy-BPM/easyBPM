package com.easy.bpm.repository.form

import com.easy.bpm.model.form.Form
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FormDefinitionRepository : JpaRepository<Form, Long> {
    fun findByFormIdOrderByVersionAsc(formId: String): List<Form>
    fun findTopByFormIdOrderByVersionDesc(formId: String): Form?
    fun findByName(name: String): List<Form>
    fun findTopByNameOrderByVersionDesc(name: String): Form?

    @Query(
        """
        select f from Form f
        where f.version = (
            select max(latest.version) from Form latest
            where latest.formId = f.formId
        )
        order by f.createdAt desc
        """
    )
    fun findLatestVersions(): List<Form>
}
