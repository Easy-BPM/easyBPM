package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProcessPageableSanitizer {
    private val processDefinitionSortableFields = setOf("id", "key", "name", "description", "version")
    private val processInstanceSortableFields = setOf("id", "status", "createdAt", "updatedAt")

    fun sanitizeProcessDefinitions(pageable: Pageable): Pageable =
        sanitize(
            pageable = pageable,
            allowedFields = processDefinitionSortableFields,
            fallbackSort = Sort.by(Sort.Order.asc("key"), Sort.Order.desc("version"))
        )

    fun sanitizeProcessInstances(pageable: Pageable): Pageable =
        sanitize(
            pageable = pageable,
            allowedFields = processInstanceSortableFields,
            fallbackSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        )

    private fun sanitize(pageable: Pageable, allowedFields: Set<String>, fallbackSort: Sort): Pageable {
        val sanitizedOrders = pageable.sort
            .filter { it.property in allowedFields }
            .toList()

        val effectiveSort = if (sanitizedOrders.isNotEmpty()) {
            Sort.by(sanitizedOrders)
        } else {
            fallbackSort
        }

        return if (pageable.isPaged) {
            PageRequest.of(pageable.pageNumber, pageable.pageSize, effectiveSort)
        } else {
            PageRequest.of(0, 100, effectiveSort)
        }
    }
}
