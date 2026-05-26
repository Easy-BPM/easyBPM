package com.easy.bpm.dto

data class ExecutionMetricsDto(
    val total: Long,
    val running: Long,
    val completed: Long,
    val failed: Long,
    val suspended: Long,
    val incidents: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProcessMetricsDto(
    val processId: String,
    val processName: String,
    val total: Long,
    val running: Long,
    val completed: Long,
    val failed: Long,
    val suspended: Long,
    val avgExecutionTimeMs: Long,
    val lastExecutedAt: String?,
    val successRate: Double
)

data class ExecutionTimeStatsDto(
    val processId: String?,
    val avgExecutionTimeMs: Long,
    val minExecutionTimeMs: Long,
    val maxExecutionTimeMs: Long,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
    val p99LatencyMs: Long,
    val totalExecutions: Long
)

data class TrendDataPoint(
    val timestamp: Long,
    val avgExecutionTimeMs: Long,
    val completedCount: Long,
    val failedCount: Long
)

data class SLAStatusDto(
    val instanceId: Long,
    val processId: String,
    val status: SLAStatus,
    val thresholdMinutes: Int,
    val elapsedTimeMinutes: Int,
    val percentageUsed: Double
)

enum class SLAStatus {
    COMPLIANT,
    AT_RISK,
    VIOLATED
}
