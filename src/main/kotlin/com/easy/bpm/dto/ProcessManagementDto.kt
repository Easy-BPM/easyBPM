package com.easy.bpm.dto

/**
 * Process list item with execution statistics
 */
data class ProcessListItemDto(
    val processId: String,
    val processName: String,
    val version: Int,
    val totalInstances: Long,
    val runningInstances: Long,
    val completedInstances: Long,
    val failedInstances: Long,
    val suspendedInstances: Long,
    val incidentCount: Long,
    val avgExecutionTimeMs: Long,
    val successRate: Double,
    val lastExecutedAt: String?,
    val createdAt: String?
)

/**
 * Paginated process list response
 */
data class ProcessListResponseDto(
    val content: List<ProcessListItemDto>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)

/**
 * Incident (failed/suspended/error process instance)
 */
data class IncidentDto(
    val instanceId: Long,
    val processId: String,
    val processName: String,
    val status: String,
    val errorMessage: String?,
    val errorType: String?,
    val currentNode: String?,
    val createdAt: String,
    val updatedAt: String,
    val nestingLevel: Int,
    val parentInstanceId: Long?
)

/**
 * Paginated incidents response
 */
data class IncidentsResponseDto(
    val content: List<IncidentDto>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int,
    val hasUnacknowledged: Boolean
)

/**
 * Process filter criteria for advanced filtering
 */
data class ProcessFilterDto(
    val processId: String? = null,
    val status: String? = null,  // ACTIVE, COMPLETED, FAILED, SUSPENDED, etc.
    val fromDate: String? = null,
    val toDate: String? = null,
    val nestingLevel: Int? = null,
    val minSuccessRate: Double? = null,
    val maxExecutionTimeMs: Long? = null,
    val page: Int = 0,
    val pageSize: Int = 20,
    val sortBy: String = "lastExecutedAt",  // lastExecutedAt, totalInstances, successRate
    val sortDirection: String = "DESC"  // ASC, DESC
)

/**
 * Incident filter criteria
 */
data class IncidentFilterDto(
    val status: String? = null,  // FAILED, SUSPENDED, ERROR
    val processId: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val acknowledged: Boolean? = null,
    val page: Int = 0,
    val pageSize: Int = 20,
    val sortBy: String = "updatedAt",
    val sortDirection: String = "DESC"
)

/**
 * Bulk action request
 */
data class BulkActionDto(
    val action: String,  // retry, acknowledge, delete
    val instanceIds: List<Long>
)

/**
 * Bulk action result
 */
data class BulkActionResultDto(
    val action: String,
    val totalProcessed: Int,
    val successful: Int,
    val failed: Int,
    val errors: List<String> = emptyList()
)
