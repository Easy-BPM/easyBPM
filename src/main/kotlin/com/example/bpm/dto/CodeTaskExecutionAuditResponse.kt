package com.example.bpm.dto

/**
 * DTO for Code Task execution audit record
 *
 * @param executionId Audit record ID
 * @param instanceId Process instance ID
 * @param nodeId Code Task node ID
 * @param jarId JAR file ID
 * @param className Class that was invoked
 * @param methodName Method that was invoked
 * @param inputVariables Input variable snapshot (JSONB)
 * @param outputVariables Output variable snapshot (JSONB)
 * @param executionTimeMs Execution time in milliseconds
 * @param status COMPLETED, FAILED, or TIMEOUT
 * @param errorMessage Error message if failed
 * @param executedAt ISO timestamp when executed
 */
data class CodeTaskExecutionAuditResponse(
    val executionId: Long,
    val instanceId: Long,
    val nodeId: String?,
    val jarId: Long?,
    val className: String?,
    val methodName: String?,
    val inputVariables: String?,
    val outputVariables: String?,
    val executionTimeMs: Int,
    val status: String,
    val errorMessage: String?,
    val executedAt: String
)

/**
 * Page wrapper for execution audit records
 *
 * @param content List of audit records
 * @param totalElements Total number of records
 * @param totalPages Total number of pages
 * @param currentPage Current page number
 */
data class ExecutionAuditPageResponse(
    val content: List<CodeTaskExecutionAuditResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int
)
