package com.easy.bpm.dto

import java.time.LocalDateTime

// Execution Time Trends
data class ExecutionTrendDto(
    val timestamp: Long,
    val averageExecutionTimeMs: Long,
    val minExecutionTimeMs: Long,
    val maxExecutionTimeMs: Long,
    val instanceCount: Long,
    val successCount: Long,
    val failureCount: Long
)

data class ExecutionTrendsResponseDto(
    val processId: String?,
    val period: String,
    val trends: List<ExecutionTrendDto>,
    val overallAverageMs: Long,
    val overallMedianMs: Long,
    val p95Ms: Long,
    val p99Ms: Long
)

// SLA Monitoring
enum class SLAStatus {
    MET, AT_RISK, VIOLATED, NOT_APPLICABLE
}

data class SLAMetricDto(
    val instanceId: Long,
    val processId: String,
    val processName: String,
    val currentNode: String?,
    val createdAt: LocalDateTime,
    val targetDurationMs: Long,
    val currentDurationMs: Long,
    val status: SLAStatus,
    val percentageComplete: Int
)

data class SLAMetricsResponseDto(
    val totalInstances: Long,
    val metInstances: Long,
    val atRiskInstances: Long,
    val violatedInstances: Long,
    val metricsPercentage: SLAPercentageDto,
    val criticalInstances: List<SLAMetricDto>,
    val timestamp: Long
)

data class SLAPercentageDto(
    val met: Double,
    val atRisk: Double,
    val violated: Double
)

// Activity Feed
enum class ActivityType {
    INSTANCE_CREATED,
    INSTANCE_COMPLETED,
    INSTANCE_FAILED,
    INSTANCE_SUSPENDED,
    NODE_EXECUTED,
    TASK_CREATED,
    TASK_COMPLETED,
    VARIABLE_UPDATED,
    CALL_ACTIVITY_STARTED,
    ERROR_CAUGHT,
    INCIDENT_CREATED
}

data class ActivityFeedItemDto(
    val id: Long,
    val timestamp: LocalDateTime,
    val type: ActivityType,
    val processId: String,
    val processName: String,
    val instanceId: Long,
    val nodeId: String?,
    val nodeName: String?,
    val description: String,
    val severity: String, // INFO, WARNING, ERROR
    val metadata: Map<String, Any>?
)

data class ActivityFeedResponseDto(
    val items: List<ActivityFeedItemDto>,
    val totalCount: Long,
    val hasMore: Boolean,
    val generatedAt: LocalDateTime
)

// Analytics Summary
data class AnalyticsSummaryDto(
    val period: String,
    val totalProcesses: Long,
    val totalInstances: Long,
    val completedInstances: Long,
    val failedInstances: Long,
    val suspendedInstances: Long,
    val runningInstances: Long,
    val averageExecutionTimeMs: Long,
    val successRate: Double,
    val slaMetStatus: SLAPercentageDto,
    val incidentsCount: Long,
    val recentActivities: List<ActivityFeedItemDto>,
    val topFailingProcesses: List<ProcessFailureRateDto>
)

data class ProcessFailureRateDto(
    val processId: String,
    val processName: String,
    val totalInstances: Long,
    val failedInstances: Long,
    val failureRate: Double
)
