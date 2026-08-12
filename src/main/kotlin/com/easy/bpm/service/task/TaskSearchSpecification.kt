package com.easy.bpm.service.task

import com.easy.bpm.controller.data.TaskFilterOperator
import com.easy.bpm.controller.data.TaskSearchFilterDto
import com.easy.bpm.controller.data.TaskVariableScope
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.time.LocalDateTime

object TaskSearchSpecification {
    fun build(
        filters: List<TaskSearchFilterDto>,
        visibleTo: TaskVisibility? = null,
        objectMapper: ObjectMapper
    ): Specification<Task> {
        return Specification { root, query, cb ->
            val criteriaQuery = query ?: throw IllegalStateException("Task search requires a criteria query")
            criteriaQuery.distinct(true)
            val predicates = mutableListOf<Predicate>()
            visibleTo?.let { predicates += visibilityPredicate(root, cb, it) }
            filters.forEach { filter ->
                predicates += filterPredicate(root, criteriaQuery, cb, filter, objectMapper)
            }
            cb.and(*predicates.toTypedArray())
        }
    }

    private fun visibilityPredicate(
        root: Root<Task>,
        cb: CriteriaBuilder,
        visibility: TaskVisibility
    ): Predicate {
        val assigneeMatches = cb.equal(root.get<String>("assignee"), visibility.username)
        val unassigned = cb.isNull(root.get<String>("assignee"))
        val candidateUserPath = root.get<MutableSet<String>>("candidateUsers")
        val candidateGroupPath = root.get<MutableSet<String>>("candidateGroups")
        val userEligible = cb.or(
            cb.isEmpty(candidateUserPath),
            cb.isMember(visibility.username, candidateUserPath)
        )
        val groupEligible = if (visibility.groups.isEmpty()) {
            cb.isEmpty(candidateGroupPath)
        } else {
            cb.or(
                cb.isEmpty(candidateGroupPath),
                cb.or(*visibility.groups.map { cb.isMember(it, candidateGroupPath) }.toTypedArray())
            )
        }
        return cb.or(assigneeMatches, cb.and(unassigned, userEligible, groupEligible))
    }

    private fun filterPredicate(
        root: Root<Task>,
        query: CriteriaQuery<*>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto,
        objectMapper: ObjectMapper
    ): Predicate {
        return when (filter.field.trim().lowercase()) {
            "state", "status" -> enumPredicate(root.get("status"), cb, filter, TaskStatus::valueOf)
            "assignee" -> assigneePredicate(root, cb, filter)
            "candidateuser", "candidate_user" -> collectionPredicate(root.get("candidateUsers"), cb, filter)
            "candidategroup", "candidate_group" -> collectionPredicate(root.get("candidateGroups"), cb, filter)
            "processinstance", "processinstanceid", "process_instance_id" ->
                comparablePredicate(root.get("processInstanceId"), cb, filter, ::toLong)
            "processdefinition", "processdefinitionid", "process_definition_id", "processdefinitionkey", "process_definition_key" ->
                processDefinitionPredicate(root, query, cb, filter)
            "taskname", "task_name", "title", "name" -> stringPredicate(root.get("title"), cb, filter)
            "createdat", "created_at", "createddate", "created_date" ->
                comparablePredicate(root.get("createdAt"), cb, filter, ::toLocalDateTime)
            "variable" -> variablePredicate(root, query, cb, filter, objectMapper)
            else -> throw IllegalArgumentException("Unsupported task filter field: ${filter.field}")
        }
    }

    private fun assigneePredicate(root: Root<Task>, cb: CriteriaBuilder, filter: TaskSearchFilterDto): Predicate {
        val values = filterValues(filter).map { it?.toString()?.trim() }
        if (values.any { it.equals("UNASSIGNED", ignoreCase = true) }) {
            val nullPredicate = cb.isNull(root.get<String>("assignee"))
            val remaining = values.filterNot { it.equals("UNASSIGNED", ignoreCase = true) }.filterNotNull()
            if (remaining.isEmpty()) return if (filter.operator == TaskFilterOperator.NOT_EQUALS || filter.operator == TaskFilterOperator.NOT_IN) {
                cb.isNotNull(root.get<String>("assignee"))
            } else {
                nullPredicate
            }
            return cb.or(nullPredicate, root.get<String>("assignee").`in`(remaining))
        }
        return stringPredicate(root.get("assignee"), cb, filter)
    }

    private fun collectionPredicate(
        path: Expression<MutableSet<String>>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto
    ): Predicate {
        val members = filterValues(filter).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        if (members.isEmpty()) throw IllegalArgumentException("${filter.field} requires a value")
        val matchAny = cb.or(*members.map { cb.isMember(it, path) }.toTypedArray())
        return when (filter.operator) {
            TaskFilterOperator.EQUALS, TaskFilterOperator.IN -> matchAny
            TaskFilterOperator.NOT_EQUALS, TaskFilterOperator.NOT_IN -> cb.not(matchAny)
            else -> throw IllegalArgumentException("Operator ${filter.operator} is not supported for ${filter.field}")
        }
    }

    private fun processDefinitionPredicate(
        root: Root<Task>,
        query: CriteriaQuery<*>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto
    ): Predicate {
        val subquery = query.subquery(Long::class.java)
        val instance = subquery.from(ProcessInstance::class.java)
        val definition = instance.get<com.easy.bpm.model.process.ProcessDefinition>("processDefinition")
        val raw = singleValue(filter)?.toString()?.trim() ?: throw IllegalArgumentException("Process definition filter requires a value")
        val definitionPredicate = raw.toLongOrNull()?.let {
            cb.equal(definition.get<Long>("id"), it)
        } ?: cb.or(
            cb.equal(definition.get<String>("key"), raw),
            cb.equal(definition.get<String>("processName"), raw)
        )
        subquery.select(instance.get("id")).where(
            cb.and(
                cb.equal(instance.get<Long>("id"), root.get<Long>("processInstanceId")),
                definitionPredicate
            )
        )
        return if (filter.operator == TaskFilterOperator.NOT_EQUALS || filter.operator == TaskFilterOperator.NOT_IN) {
            cb.not(cb.exists(subquery))
        } else {
            cb.exists(subquery)
        }
    }

    private fun variablePredicate(
        root: Root<Task>,
        query: CriteriaQuery<*>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto,
        objectMapper: ObjectMapper
    ): Predicate {
        val variableName = filter.name?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Variable filters require a name")
        return if (filter.scope == TaskVariableScope.PROCESS) {
            val subquery = query.subquery(Long::class.java)
            val variableRoot = subquery.from(ProcessVariable::class.java)
            subquery.select(variableRoot.get("id")).where(
                cb.and(
                    cb.equal(variableRoot.get<Long>("processInstanceId"), root.get<Long>("processInstanceId")),
                    cb.equal(variableRoot.get<String>("name"), variableName),
                    jsonValuePredicate(variableRoot.get<Any>("value").`as`(String::class.java), cb, filter, objectMapper)
                )
            )
            cb.exists(subquery)
        } else {
            val subquery = query.subquery(Long::class.java)
            val variableRoot = subquery.from(TaskVariable::class.java)
            subquery.select(variableRoot.get("id")).where(
                cb.and(
                    cb.equal(variableRoot.get<Long>("taskId"), root.get<Long>("id")),
                    cb.equal(variableRoot.get<String>("name"), variableName),
                    jsonValuePredicate(variableRoot.get<Any>("value").`as`(String::class.java), cb, filter, objectMapper)
                )
            )
            cb.exists(subquery)
        }
    }

    private fun jsonValuePredicate(
        path: Expression<String>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto,
        objectMapper: ObjectMapper
    ): Predicate {
        val encoded = filterValues(filter).map { objectMapper.writeValueAsString(it) }
        val raw = filterValues(filter).map { it?.toString().orEmpty() }
        val candidates = (encoded + raw).distinct()
        return stringPredicate(path, cb, filter.copy(value = null, values = candidates))
    }

    private fun stringPredicate(path: Expression<String>, cb: CriteriaBuilder, filter: TaskSearchFilterDto): Predicate {
        val values = filterValues(filter).mapNotNull { it?.toString() }
        if (values.isEmpty()) throw IllegalArgumentException("${filter.field} requires a value")
        return when (filter.operator) {
            TaskFilterOperator.EQUALS -> cb.equal(path, values.first())
            TaskFilterOperator.NOT_EQUALS -> cb.notEqual(path, values.first())
            TaskFilterOperator.IN -> path.`in`(values)
            TaskFilterOperator.NOT_IN -> cb.not(path.`in`(values))
            TaskFilterOperator.CONTAINS -> cb.like(cb.lower(path), "%${escapeLike(values.first().lowercase())}%", '\\')
            TaskFilterOperator.STARTS_WITH -> cb.like(cb.lower(path), "${escapeLike(values.first().lowercase())}%", '\\')
            TaskFilterOperator.ENDS_WITH -> cb.like(cb.lower(path), "%${escapeLike(values.first().lowercase())}", '\\')
            else -> throw IllegalArgumentException("Operator ${filter.operator} is not supported for ${filter.field}")
        }
    }

    private fun <T : Comparable<T>> comparablePredicate(
        path: Expression<T>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto,
        converter: (Any?) -> T
    ): Predicate {
        val values = filterValues(filter).map(converter)
        if (values.isEmpty()) throw IllegalArgumentException("${filter.field} requires a value")
        return when (filter.operator) {
            TaskFilterOperator.EQUALS -> cb.equal(path, values.first())
            TaskFilterOperator.NOT_EQUALS -> cb.notEqual(path, values.first())
            TaskFilterOperator.IN -> path.`in`(values)
            TaskFilterOperator.NOT_IN -> cb.not(path.`in`(values))
            TaskFilterOperator.GREATER_THAN -> cb.greaterThan(path, values.first())
            TaskFilterOperator.GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo(path, values.first())
            TaskFilterOperator.LESS_THAN -> cb.lessThan(path, values.first())
            TaskFilterOperator.LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo(path, values.first())
            else -> throw IllegalArgumentException("Operator ${filter.operator} is not supported for ${filter.field}")
        }
    }

    private fun <E : Enum<E>> enumPredicate(
        path: Expression<E>,
        cb: CriteriaBuilder,
        filter: TaskSearchFilterDto,
        converter: (String) -> E
    ): Predicate {
        return comparablePredicate(path, cb, filter) { converter(it.toString().uppercase()) }
    }

    private fun filterValues(filter: TaskSearchFilterDto): List<Any?> =
        filter.values?.takeIf { it.isNotEmpty() } ?: listOf(filter.value)

    private fun singleValue(filter: TaskSearchFilterDto): Any? = filterValues(filter).firstOrNull()

    private fun toLong(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        else -> value.toString().toLong()
    }

    private fun toLocalDateTime(value: Any?): LocalDateTime {
        val text = value.toString()
        return runCatching { LocalDateTime.parse(text) }
            .getOrElse { LocalDate.parse(text).atStartOfDay() }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

data class TaskVisibility(
    val username: String,
    val groups: Set<String>
)
