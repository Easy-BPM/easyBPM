package com.easy.bpm.controller

import com.easy.bpm.dto.ExecutionMetricsDto
import com.easy.bpm.dto.ProcessMetricsDto
import com.easy.bpm.dto.ExecutionTimeStatsDto
import com.easy.bpm.dto.TrendDataPoint
import com.easy.bpm.service.ExecutionMetricsService
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/admin/metrics")
@CrossOrigin(origins = ["*"])
class ExecutionMetricsController(
    private val executionMetricsService: ExecutionMetricsService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ExecutionMetricsController::class.java)
    }

    /**
     * GET /admin/metrics/execution - Get overall execution metrics
     *
     * Query Parameters:
     *   - from: Optional start date (ISO 8601)
     *   - to: Optional end date (ISO 8601)
     *
     * @return ExecutionMetricsDto with total, running, completed, failed, suspended, incidents counts
     */
    @GetMapping("/execution")
    fun getExecutionMetrics(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: LocalDateTime?
    ): ResponseEntity<ExecutionMetricsDto> {
        return try {
            val metrics = executionMetricsService.getExecutionMetrics(from, to)
            ResponseEntity.ok(metrics)
        } catch (e: Exception) {
            logger.error("Error fetching execution metrics", e)
            ResponseEntity.status(500).body(
                ExecutionMetricsDto(0, 0, 0, 0, 0, 0)
            )
        }
    }

    /**
     * GET /admin/metrics/processes - Get execution metrics grouped by process
     *
     * @return List of ProcessMetricsDto with stats per process
     */
    @GetMapping("/processes")
    fun getMetricsPerProcess(): ResponseEntity<List<ProcessMetricsDto>> {
        return try {
            val metrics = executionMetricsService.getMetricsPerProcess()
            ResponseEntity.ok(metrics)
        } catch (e: Exception) {
            logger.error("Error fetching process metrics", e)
            ResponseEntity.status(500).body(emptyList())
        }
    }

    /**
     * GET /admin/metrics/execution-time - Get execution time statistics
     *
     * Query Parameters:
     *   - processId: Optional process ID/key for specific process (null = all processes)
     *
     * @return ExecutionTimeStatsDto with avg, min, max, percentiles
     */
    @GetMapping("/execution-time")
    fun getExecutionTimeStats(
        @RequestParam(required = false) processId: String?
    ): ResponseEntity<ExecutionTimeStatsDto> {
        return try {
            val stats = executionMetricsService.getExecutionTimeStats(processId)
            ResponseEntity.ok(stats)
        } catch (e: Exception) {
            logger.error("Error fetching execution time stats", e)
            ResponseEntity.status(500).body(
                ExecutionTimeStatsDto(processId, 0, 0, 0, 0, 0, 0, 0)
            )
        }
    }

    /**
     * GET /admin/metrics/trends - Get execution time trends over time
     *
     * Query Parameters:
     *   - processId: Optional process ID/key
     *   - bucketSize: Bucket size in minutes (default: 60)
     *
     * @return List of TrendDataPoint with historical execution data
     */
    @GetMapping("/trends")
    fun getExecutionTrends(
        @RequestParam(required = false) processId: String?,
        @RequestParam(defaultValue = "60") bucketSize: Int
    ): ResponseEntity<List<TrendDataPoint>> {
        return try {
            val trends = executionMetricsService.getExecutionTrendOverTime(processId, bucketSize)
            ResponseEntity.ok(trends)
        } catch (e: Exception) {
            logger.error("Error fetching execution trends", e)
            ResponseEntity.status(500).body(emptyList())
        }
    }

    /**
     * Health check endpoint for metrics service
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "OK"))
    }
}
