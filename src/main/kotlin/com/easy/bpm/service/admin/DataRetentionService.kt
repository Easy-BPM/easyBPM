package com.easy.bpm.service.admin

import com.easy.bpm.config.DataRetentionProperties
import com.easy.bpm.controller.data.DataRetentionSettingsResponse
import com.easy.bpm.controller.data.MaintenanceCleanupSummary
import com.easy.bpm.controller.data.PurgeCompletedInstancesRequest
import com.easy.bpm.controller.data.PurgeCompletedTasksRequest
import com.easy.bpm.controller.data.UpdateDataRetentionSettingsRequest
import com.easy.bpm.model.admin.DataRetentionSettings
import com.easy.bpm.repository.admin.DataRetentionSettingsRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.TriggerContext
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime

@Service
class DataRetentionService(
    private val properties: DataRetentionProperties,
    private val settingsRepository: DataRetentionSettingsRepository,
    private val maintenanceService: AdminMaintenanceService
) : SchedulingConfigurer {
    private val log = LoggerFactory.getLogger(DataRetentionService::class.java)

    companion object {
        private const val SETTINGS_ID = 1L
        private const val MIN_RETENTION_DAYS = 1L
        private const val MAX_RETENTION_DAYS = 3650L
        private const val MIN_BATCH_SIZE = 1
        private const val MAX_BATCH_SIZE = 10_000
    }

    fun settings(): DataRetentionSettingsResponse =
        currentSettings().toResponse()

    @Transactional
    fun updateSettings(request: UpdateDataRetentionSettingsRequest): DataRetentionSettingsResponse {
        validate(request)

        val settings = currentSettings()
        settings.enabled = request.enabled
        settings.completedProcessRetentionDays = request.completedProcessRetentionDays
        settings.completedTaskRetentionDays = request.completedTaskRetentionDays
        settings.batchSize = request.batchSize
        settings.cron = request.cron.trim()
        settings.updatedAt = LocalDateTime.now()

        return settingsRepository.save(settings).toResponse()
    }

    fun previewConfiguredRetention(now: LocalDateTime = LocalDateTime.now()): MaintenanceCleanupSummary {
        val processSummary = maintenanceService.previewPurgeCompletedInstances(configuredProcessRequest(now))
        val taskSummary = maintenanceService.previewPurgeCompletedTasks(
            request = configuredTaskRequest(now),
            excludedProcessInstanceIds = processSummary.candidateInstanceIds
        )
        return processSummary + taskSummary
    }

    fun runConfiguredRetention(now: LocalDateTime = LocalDateTime.now()): MaintenanceCleanupSummary {
        val processSummary = maintenanceService.purgeCompletedInstances(configuredProcessRequest(now).copy(dryRun = false))
        val taskSummary = maintenanceService.purgeCompletedTasks(configuredTaskRequest(now).copy(dryRun = false))
        return processSummary + taskSummary
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addTriggerTask({ runScheduledRetention() }, retentionTrigger())
    }

    fun runScheduledRetention() {
        val settings = currentSettings()
        if (!settings.enabled) {
            return
        }

        val summary = runConfiguredRetention()
        if (summary.processInstancesDeleted > 0) {
            log.info(
                "Data retention purged {} completed process instances, {} tasks, {} process variables and {} task variables",
                summary.processInstancesDeleted,
                summary.tasksDeleted,
                summary.processVariablesDeleted,
                summary.taskVariablesDeleted
            )
        }
    }

    private fun configuredProcessRequest(now: LocalDateTime): PurgeCompletedInstancesRequest {
        val settings = currentSettings()
        return PurgeCompletedInstancesRequest(
            completedBefore = now.minusDays(settings.completedProcessRetentionDays.coerceAtLeast(1)),
            batchSize = settings.batchSize,
            dryRun = true
        )
    }

    private fun configuredTaskRequest(now: LocalDateTime): PurgeCompletedTasksRequest {
        val settings = currentSettings()
        return PurgeCompletedTasksRequest(
            completedBefore = now.minusDays(settings.completedTaskRetentionDays.coerceAtLeast(1)),
            batchSize = settings.batchSize,
            dryRun = true
        )
    }

    private fun currentSettings(): DataRetentionSettings =
        settingsRepository.findById(SETTINGS_ID).orElseGet {
            settingsRepository.save(
                DataRetentionSettings(
                    id = SETTINGS_ID,
                    enabled = properties.enabled,
                    completedProcessRetentionDays = properties.completedProcessRetentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS),
                    completedTaskRetentionDays = properties.completedTaskRetentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS),
                    batchSize = properties.batchSize.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE),
                    cron = properties.cron.takeIf { CronExpression.isValidExpression(it) } ?: "0 0 3 * * *"
                )
            )
        }

    private fun retentionTrigger(): Trigger =
        Trigger { context: TriggerContext ->
            val settings = currentSettings()
            val cron = if (CronExpression.isValidExpression(settings.cron)) settings.cron else properties.cron
            CronTrigger(cron).nextExecution(context)
                ?: Instant.now().plusSeconds(86_400)
        }

    private fun validate(request: UpdateDataRetentionSettingsRequest) {
        require(request.completedProcessRetentionDays in MIN_RETENTION_DAYS..MAX_RETENTION_DAYS) {
            "completedProcessRetentionDays must be between $MIN_RETENTION_DAYS and $MAX_RETENTION_DAYS"
        }
        require(request.completedTaskRetentionDays in MIN_RETENTION_DAYS..MAX_RETENTION_DAYS) {
            "completedTaskRetentionDays must be between $MIN_RETENTION_DAYS and $MAX_RETENTION_DAYS"
        }
        require(request.batchSize in MIN_BATCH_SIZE..MAX_BATCH_SIZE) {
            "batchSize must be between $MIN_BATCH_SIZE and $MAX_BATCH_SIZE"
        }
        require(CronExpression.isValidExpression(request.cron.trim())) {
            "cron must be a valid Spring cron expression"
        }
    }

    private fun DataRetentionSettings.toResponse(): DataRetentionSettingsResponse =
        DataRetentionSettingsResponse(
            enabled = enabled,
            completedProcessRetentionDays = completedProcessRetentionDays,
            completedTaskRetentionDays = completedTaskRetentionDays,
            batchSize = batchSize,
            cron = cron
        )

    private operator fun MaintenanceCleanupSummary.plus(other: MaintenanceCleanupSummary): MaintenanceCleanupSummary =
        MaintenanceCleanupSummary(
            dryRun = dryRun && other.dryRun,
            processDefinitionsDeleted = processDefinitionsDeleted + other.processDefinitionsDeleted,
            processInstancesDeleted = processInstancesDeleted + other.processInstancesDeleted,
            tasksDeleted = tasksDeleted + other.tasksDeleted,
            processVariablesDeleted = processVariablesDeleted + other.processVariablesDeleted,
            taskVariablesDeleted = taskVariablesDeleted + other.taskVariablesDeleted,
            documentsDeleted = documentsDeleted + other.documentsDeleted,
            messageSubscriptionsDeleted = messageSubscriptionsDeleted + other.messageSubscriptionsDeleted,
            workerRequestsDeleted = workerRequestsDeleted + other.workerRequestsDeleted,
            codeTaskExecutionsDeleted = codeTaskExecutionsDeleted + other.codeTaskExecutionsDeleted,
            incidentsDeleted = incidentsDeleted + other.incidentsDeleted,
            incidentEventsDeleted = incidentEventsDeleted + other.incidentEventsDeleted,
            timelineEventsDeleted = timelineEventsDeleted + other.timelineEventsDeleted,
            callActivityMappingsDeleted = callActivityMappingsDeleted + other.callActivityMappingsDeleted,
            candidateInstanceIds = candidateInstanceIds + other.candidateInstanceIds,
            candidateTaskIds = candidateTaskIds + other.candidateTaskIds
        )
}
