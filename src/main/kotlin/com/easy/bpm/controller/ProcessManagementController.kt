package com.easy.bpm.controller

import com.easy.bpm.dto.ProcessListItemDto
import com.easy.bpm.dto.IncidentDto
import com.easy.bpm.dto.ProcessListResponseDto
import com.easy.bpm.dto.IncidentsResponseDto
import com.easy.bpm.service.ExecutionMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/processes")
@CrossOrigin(origins = ["*"])
class ProcessManagementController(
    private val executionMetricsService: ExecutionMetricsService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessManagementController::class.java)
    }

    /**
     * GET /admin/processes/list - Get list of all processes with statistics
     *
     * Query Parameters:
     *   - page: Page number (default 0)
     *   - pageSize: Items per page (default 20)
     *   - sortBy: Sort field (default lastExecutedAt)
     */
    @GetMapping("/list")
    fun getProcessList(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "lastExecutedAt") sortBy: String
    ): ResponseEntity<ProcessListResponseDto> {
        return try {
            val processes = executionMetricsService.getProcessList(page, pageSize, sortBy)
            
            val response = ProcessListResponseDto(
                content = processes,
                totalElements = processes.size.toLong(),
                totalPages = (processes.size + pageSize - 1) / pageSize,
                currentPage = page,
                pageSize = pageSize
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error fetching process list", e)
            ResponseEntity.status(500).body(
                ProcessListResponseDto(emptyList(), 0, 0, page, pageSize)
            )
        }
    }

    /**
     * GET /admin/processes/incidents - Get list of incidents (failed/suspended/error instances)
     *
     * Query Parameters:
     *   - page: Page number (default 0)
     *   - pageSize: Items per page (default 20)
     */
    @GetMapping("/incidents")
    fun getIncidents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<IncidentsResponseDto> {
        return try {
            val incidents = executionMetricsService.getIncidents(page, pageSize)
            
            val response = IncidentsResponseDto(
                content = incidents,
                totalElements = incidents.size.toLong(),
                totalPages = (incidents.size + pageSize - 1) / pageSize,
                currentPage = page,
                pageSize = pageSize,
                hasUnacknowledged = incidents.isNotEmpty()
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error fetching incidents", e)
            ResponseEntity.status(500).body(
                IncidentsResponseDto(emptyList(), 0, 0, page, pageSize, false)
            )
        }
    }

    /**
     * GET /admin/processes/{processId} - Get specific process details
     */
    @GetMapping("/{processId}")
    fun getProcessDetails(
        @PathVariable processId: String
    ): ResponseEntity<ProcessListItemDto> {
        return try {
            val processes = executionMetricsService.getProcessList()
            val process = processes.find { it.processId == processId }
            
            if (process != null) {
                ResponseEntity.ok(process)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error("Error fetching process details for $processId", e)
            ResponseEntity.status(500).build()
        }
    }

    /**
     * GET /admin/processes/health - Health check endpoint
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "OK"))
    }
}
