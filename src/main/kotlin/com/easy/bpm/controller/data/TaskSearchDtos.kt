package com.easy.bpm.controller.data

enum class TaskFilterOperator {
    EQUALS,
    NOT_EQUALS,
    IN,
    NOT_IN,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH
}

enum class TaskVariableScope {
    TASK,
    PROCESS
}

data class TaskSearchFilterDto(
    val field: String,
    val operator: TaskFilterOperator = TaskFilterOperator.EQUALS,
    val value: Any? = null,
    val values: List<Any?>? = null,
    val scope: TaskVariableScope? = null,
    val name: String? = null
)

data class TaskSearchRequestDto(
    val filters: List<TaskSearchFilterDto> = emptyList()
)
