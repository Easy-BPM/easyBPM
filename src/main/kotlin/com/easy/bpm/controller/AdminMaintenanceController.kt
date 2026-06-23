package com.easy.bpm.controller

import com.easy.bpm.controller.data.MaintenanceCleanupSummary
import com.easy.bpm.controller.data.PurgeCompletedInstancesRequest
import com.easy.bpm.service.AdminMaintenanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/maintenance")
@Tag(name = "Admin Maintenance", description = "Purge and archive maintenance operations")
class AdminMaintenanceController(
    private val maintenanceService: AdminMaintenanceService
) {
    @PostMapping("/purge-completed-instances")
    @Operation(summary = "Purge completed instances", description = "Preview or purge completed process instances older than a cutoff date")
    fun purgeCompletedInstances(
        @RequestBody request: PurgeCompletedInstancesRequest
    ): ResponseEntity<MaintenanceCleanupSummary> =
        ResponseEntity.ok(maintenanceService.purgeCompletedInstances(request))

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
