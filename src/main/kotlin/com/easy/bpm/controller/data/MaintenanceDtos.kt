package com.easy.bpm.controller.data

import java.time.LocalDateTime

data class PurgeCompletedInstancesRequest(
    val completedBefore: LocalDateTime,
    val processDefinitionId: Long? = null,
    val processKey: String? = null,
    val batchSize: Int? = null,
    val dryRun: Boolean = true
)

data class PurgeCompletedTasksRequest(
    val completedBefore: LocalDateTime,
    val batchSize: Int? = null,
    val dryRun: Boolean = true
)

data class DataRetentionSettingsResponse(
    val enabled: Boolean,
    val completedProcessRetentionDays: Long,
    val completedTaskRetentionDays: Long,
    val batchSize: Int,
    val cron: String
)

data class UpdateDataRetentionSettingsRequest(
    val enabled: Boolean,
    val completedProcessRetentionDays: Long,
    val completedTaskRetentionDays: Long,
    val batchSize: Int,
    val cron: String
)

data class MaintenanceCleanupSummary(
    val dryRun: Boolean,
    val processDefinitionsDeleted: Int = 0,
    val processInstancesDeleted: Int = 0,
    val tasksDeleted: Int = 0,
    val processVariablesDeleted: Int = 0,
    val taskVariablesDeleted: Int = 0,
    val documentsDeleted: Int = 0,
    val messageSubscriptionsDeleted: Int = 0,
    val workerRequestsDeleted: Int = 0,
    val codeTaskExecutionsDeleted: Int = 0,
    val incidentsDeleted: Int = 0,
    val incidentEventsDeleted: Int = 0,
    val timelineEventsDeleted: Int = 0,
    val callActivityMappingsDeleted: Int = 0,
    val candidateInstanceIds: List<Long> = emptyList(),
    val candidateTaskIds: List<Long> = emptyList()
)
