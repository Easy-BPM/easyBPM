package com.easy.bpm.controller

import com.easy.bpm.controller.data.DataRetentionSettingsResponse
import com.easy.bpm.controller.data.MaintenanceCleanupSummary
import com.easy.bpm.controller.data.PurgeCompletedInstancesRequest
import com.easy.bpm.controller.data.PurgeCompletedTasksRequest
import com.easy.bpm.controller.data.UpdateDataRetentionSettingsRequest
import com.easy.bpm.service.admin.AdminMaintenanceService
import com.easy.bpm.service.admin.DataRetentionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/maintenance")
@Tag(name = "Admin Maintenance", description = "Purge and archive maintenance operations")
class AdminMaintenanceController(
    private val maintenanceService: AdminMaintenanceService,
    private val dataRetentionService: DataRetentionService
) {
    @GetMapping("/retention")
    @Operation(summary = "Get data retention settings", description = "Return configured data retention settings")
    fun getRetentionSettings(): ResponseEntity<DataRetentionSettingsResponse> =
        ResponseEntity.ok(dataRetentionService.settings())

    @PutMapping("/retention")
    @Operation(summary = "Update data retention settings", description = "Persist data retention settings used by scheduled cleanup")
    fun updateRetentionSettings(
        @RequestBody request: UpdateDataRetentionSettingsRequest
    ): ResponseEntity<DataRetentionSettingsResponse> =
        try {
            ResponseEntity.ok(dataRetentionService.updateSettings(request))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }

    @PostMapping("/retention/preview")
    @Operation(summary = "Preview configured retention", description = "Preview cleanup using configured data retention settings")
    fun previewConfiguredRetention(): ResponseEntity<MaintenanceCleanupSummary> =
        ResponseEntity.ok(dataRetentionService.previewConfiguredRetention())

    @PostMapping("/retention/run")
    @Operation(summary = "Run configured retention", description = "Run cleanup using configured data retention settings")
    fun runConfiguredRetention(): ResponseEntity<MaintenanceCleanupSummary> {
        val settings = dataRetentionService.settings()
        if (!settings.enabled) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        return ResponseEntity.ok(dataRetentionService.runConfiguredRetention())
    }

    @PostMapping("/purge-completed-instances")
    @Operation(summary = "Purge completed instances", description = "Preview or purge completed process instances older than a cutoff date")
    fun purgeCompletedInstances(
        @RequestBody request: PurgeCompletedInstancesRequest
    ): ResponseEntity<MaintenanceCleanupSummary> =
        ResponseEntity.ok(maintenanceService.purgeCompletedInstances(request))

    @PostMapping("/purge-completed-tasks")
    @Operation(summary = "Purge completed tasks", description = "Preview or purge completed tasks older than a cutoff date")
    fun purgeCompletedTasks(
        @RequestBody request: PurgeCompletedTasksRequest
    ): ResponseEntity<MaintenanceCleanupSummary> =
        ResponseEntity.ok(maintenanceService.purgeCompletedTasks(request))

    @DeleteMapping("/process-definitions/{id}")
    @Operation(summary = "Delete process definition", description = "Delete a process definition and all related runtime data")
    fun deleteProcessDefinition(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "true") dryRun: Boolean
    ): ResponseEntity<MaintenanceCleanupSummary> =
        try {
            ResponseEntity.ok(maintenanceService.deleteProcessDefinition(id, dryRun))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
}
