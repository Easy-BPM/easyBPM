package com.easy.bpm.controller

import com.easy.bpm.service.ExecutionMetricsService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/analytics")
@CrossOrigin(origins = ["*"])
class AnalyticsController(
    private val executionMetricsService: ExecutionMetricsService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AnalyticsController::class.java)
    }

    /**
     * Phase 9.3: Get execution time trends
     */
    @GetMapping("/trends")
    fun getExecutionTrends(
        @RequestParam(required = false) processId: String?,
        @RequestParam(defaultValue = "60") bucketSizeMinutes: Int,
        @RequestParam(defaultValue = "24") hoursBack: Int
    ) = try {
        executionMetricsService.getExecutionTrends(processId, bucketSizeMinutes, hoursBack)
    } catch (e: Exception) {
        logger.error("Error getting execution trends", e)
        throw e
    }

    /**
     * Phase 9.3: Get SLA metrics
     */
    @GetMapping("/sla")
    fun getSLAMetrics() = try {
        executionMetricsService.getSLAMetrics()
    } catch (e: Exception) {
        logger.error("Error getting SLA metrics", e)
        throw e
    }

    /**
     * Phase 9.3: Get activity feed
     */
    @GetMapping("/activity-feed")
    fun getActivityFeed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int
    ) = try {
        executionMetricsService.getActivityFeed(page, pageSize)
    } catch (e: Exception) {
        logger.error("Error getting activity feed", e)
        throw e
    }

    /**
     * Phase 9.3: Get analytics summary
     */
    @GetMapping("/summary")
    fun getAnalyticsSummary(
        @RequestParam(defaultValue = "24h") period: String
    ) = try {
        executionMetricsService.getAnalyticsSummary(period)
    } catch (e: Exception) {
        logger.error("Error getting analytics summary", e)
        throw e
    }

    /**
     * Health check for analytics module
     */
    @GetMapping("/health")
    fun health() = mapOf("status" to "OK", "timestamp" to System.currentTimeMillis())
}
