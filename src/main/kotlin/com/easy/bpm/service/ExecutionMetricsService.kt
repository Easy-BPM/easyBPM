package com.easy.bpm.service

import com.easy.bpm.dto.ExecutionMetricsDto
import com.easy.bpm.dto.ProcessMetricsDto
import com.easy.bpm.dto.ExecutionTimeStatsDto
import com.easy.bpm.dto.TrendDataPoint
import com.easy.bpm.dto.ProcessListItemDto
import com.easy.bpm.dto.IncidentDto
import com.easy.bpm.dto.ExecutionTrendDto
import com.easy.bpm.dto.ExecutionTrendsResponseDto
import com.easy.bpm.dto.SLAMetricDto
import com.easy.bpm.dto.SLAMetricsResponseDto
import com.easy.bpm.dto.SLAPercentageDto
import com.easy.bpm.dto.SLAStatusEnum
import com.easy.bpm.dto.ActivityFeedItemDto
import com.easy.bpm.dto.ActivityFeedResponseDto
import com.easy.bpm.dto.ActivityType
import com.easy.bpm.dto.AnalyticsSummaryDto
import com.easy.bpm.dto.ProcessFailureRateDto
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

@Service
class ExecutionMetricsService(
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processDefinitionRepository: ProcessDefinitionRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ExecutionMetricsService::class.java)
    }

    /**
     * Get overall execution metrics
     */
    fun getExecutionMetrics(fromDate: LocalDateTime? = null, toDate: LocalDateTime? = null): ExecutionMetricsDto {
        val now = LocalDateTime.now()
        val from = fromDate ?: now.minusDays(1)
        val to = toDate ?: now

        val total = if (fromDate != null) {
            processInstanceRepository.countByCreatedAtBetween(from, to)
        } else {
            processInstanceRepository.count()
        }

        val running = processInstanceRepository.countByStatus(ProcessStatus.ACTIVE)
        val completed = processInstanceRepository.countByStatus(ProcessStatus.COMPLETED)
        val failed = processInstanceRepository.countByStatus(ProcessStatus.FAILED)
        val suspended = processInstanceRepository.countByStatus(ProcessStatus.SUSPENDED)
        
        // Incidents = FAILED + SUSPENDED + ERROR
        val incidents = failed + suspended + processInstanceRepository.countByStatus(ProcessStatus.ERROR)

        return ExecutionMetricsDto(
            total = total,
            running = running,
            completed = completed,
            failed = failed,
            suspended = suspended,
            incidents = incidents,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get execution metrics grouped by process
     */
    fun getMetricsPerProcess(): List<ProcessMetricsDto> {
        val definitions = processDefinitionRepository.findAll()
        
        return definitions.map { definition ->
            val instances = processInstanceRepository.findByProcessDefinitionId(definition.id!!)
            
            val total = instances.size.toLong()
            val running = instances.count { it.status == ProcessStatus.ACTIVE }.toLong()
            val completed = instances.count { it.status == ProcessStatus.COMPLETED }.toLong()
            val failed = instances.count { it.status == ProcessStatus.FAILED }.toLong()
            val suspended = instances.count { it.status == ProcessStatus.SUSPENDED }.toLong()

            // Calculate average execution time
            val completedInstances = instances.filter { it.status == ProcessStatus.COMPLETED }
            val avgExecutionTime = if (completedInstances.isNotEmpty()) {
                completedInstances.mapNotNull { instance ->
                    if (instance.createdAt != null && instance.updatedAt != null) {
                        ChronoUnit.MILLIS.between(instance.createdAt, instance.updatedAt)
                    } else {
                        null
                    }
                }.takeIf { it.isNotEmpty() }?.let { times ->
                    (times.sum() / times.size).toLong()
                } ?: 0L
            } else {
                0L
            }

            // Success rate
            val successRate = if (total > 0) {
                (completed.toDouble() / total.toDouble()) * 100
            } else {
                0.0
            }

            // Last executed at
            val lastExecutedAt = instances.maxByOrNull { it.updatedAt ?: it.createdAt!! }?.updatedAt?.toString()

            ProcessMetricsDto(
                processId = definition.key,
                processName = definition.processName ?: definition.key,
                total = total,
                running = running,
                completed = completed,
                failed = failed,
                suspended = suspended,
                avgExecutionTimeMs = avgExecutionTime,
                lastExecutedAt = lastExecutedAt,
                successRate = (successRate * 100).roundToLong() / 100.0
            )
        }
    }

    /**
     * Get execution time statistics for a process or all processes
     */
    fun getExecutionTimeStats(processId: String? = null): ExecutionTimeStatsDto {
        val instances = if (processId != null) {
            val definition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
                ?: return ExecutionTimeStatsDto(processId, 0, 0, 0, 0, 0, 0, 0)
            processInstanceRepository.findByProcessDefinitionId(definition.id!!)
        } else {
            processInstanceRepository.findAll()
        }

        val completedInstances = instances.filter { it.status == ProcessStatus.COMPLETED }
        
        if (completedInstances.isEmpty()) {
            return ExecutionTimeStatsDto(processId, 0, 0, 0, 0, 0, 0, 0)
        }

        val executionTimes = completedInstances.mapNotNull { instance ->
            if (instance.createdAt != null && instance.updatedAt != null) {
                ChronoUnit.MILLIS.between(instance.createdAt, instance.updatedAt)
            } else {
                null
            }
        }.sorted()

        if (executionTimes.isEmpty()) {
            return ExecutionTimeStatsDto(processId, 0, 0, 0, 0, 0, 0, 0)
        }

        val avg = executionTimes.average().toLong()
        val min = executionTimes.minOrNull() ?: 0L
        val max = executionTimes.maxOrNull() ?: 0L
        
        val p50 = calculatePercentile(executionTimes, 0.5)
        val p95 = calculatePercentile(executionTimes, 0.95)
        val p99 = calculatePercentile(executionTimes, 0.99)

        return ExecutionTimeStatsDto(
            processId = processId,
            avgExecutionTimeMs = avg,
            minExecutionTimeMs = min,
            maxExecutionTimeMs = max,
            p50LatencyMs = p50,
            p95LatencyMs = p95,
            p99LatencyMs = p99,
            totalExecutions = executionTimes.size.toLong()
        )
    }

    /**
     * Get execution time trends over time
     */
    fun getExecutionTrendOverTime(processId: String? = null, bucketSizeMinutes: Int = 60): List<TrendDataPoint> {
        val instances = if (processId != null) {
            val definition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
                ?: return emptyList()
            processInstanceRepository.findByProcessDefinitionId(definition.id!!)
        } else {
            processInstanceRepository.findAll()
        }

        if (instances.isEmpty()) {
            return emptyList()
        }

        // Group by time buckets
        val buckets = mutableMapOf<Long, MutableList<Long>>()
        val completedCounts = mutableMapOf<Long, Long>()
        val failedCounts = mutableMapOf<Long, Long>()

        instances.forEach { instance ->
            val timestamp = instance.createdAt?.let { 
                (it.toLocalDate().atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000)
            } ?: return@forEach

            // Completed instances with execution time
            if (instance.status == ProcessStatus.COMPLETED && instance.updatedAt != null) {
                val executionTime = ChronoUnit.MILLIS.between(instance.createdAt, instance.updatedAt)
                buckets.getOrPut(timestamp) { mutableListOf() }.add(executionTime)
            }

            // Count completed and failed
            val dateKey = instance.createdAt?.toLocalDate()?.let {
                it.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
            } ?: return@forEach

            if (instance.status == ProcessStatus.COMPLETED) {
                completedCounts[dateKey] = (completedCounts[dateKey] ?: 0L) + 1
            } else if (instance.status == ProcessStatus.FAILED) {
                failedCounts[dateKey] = (failedCounts[dateKey] ?: 0L) + 1
            }
        }

        // Convert to trend data points
        return buckets.map { (timestamp, times) ->
            val avgTime = if (times.isNotEmpty()) times.average().toLong() else 0L
            TrendDataPoint(
                timestamp = timestamp,
                avgExecutionTimeMs = avgTime,
                completedCount = completedCounts[timestamp] ?: 0L,
                failedCount = failedCounts[timestamp] ?: 0L
            )
        }.sortedBy { it.timestamp }
    }

    /**
     * Calculate percentile value in a sorted list
     */
    private fun calculatePercentile(sortedList: List<Long>, percentile: Double): Long {
        if (sortedList.isEmpty()) return 0L
        
        val index = ((percentile / 100.0) * (sortedList.size - 1)).toInt()
        return sortedList[index.coerceIn(0, sortedList.size - 1)]
    }

    /**
     * Get process list with statistics
     */
    fun getProcessList(page: Int = 0, pageSize: Int = 20, sortBy: String = "lastExecutedAt"): List<ProcessListItemDto> {
        val definitions = processDefinitionRepository.findAll()

        return definitions.map { definition ->
            val instances = processInstanceRepository.findByProcessDefinitionId(definition.id!!)
            val total = instances.size.toLong()
            val running = instances.count { it.status == ProcessStatus.ACTIVE }.toLong()
            val completed = instances.count { it.status == ProcessStatus.COMPLETED }.toLong()
            val failed = instances.count { it.status == ProcessStatus.FAILED }.toLong()
            val suspended = instances.count { it.status == ProcessStatus.SUSPENDED }.toLong()
            val incidents = failed + suspended + instances.count { it.status == ProcessStatus.ERROR }

            val completedInstances = instances.filter { it.status == ProcessStatus.COMPLETED }
            val avgExecutionTime = if (completedInstances.isNotEmpty()) {
                completedInstances.mapNotNull { instance ->
                    if (instance.createdAt != null && instance.updatedAt != null) {
                        ChronoUnit.MILLIS.between(instance.createdAt, instance.updatedAt)
                    } else null
                }.takeIf { it.isNotEmpty() }?.let { times ->
                    (times.sum() / times.size).toLong()
                } ?: 0L
            } else 0L

            val successRate = if (total > 0) {
                (completed.toDouble() / total.toDouble()) * 100
            } else 0.0

            val lastExecutedAt = instances.maxByOrNull { it.updatedAt ?: it.createdAt!! }?.updatedAt?.toString()
            val createdAt = definition.key  // Use process key as created reference

            ProcessListItemDto(
                processId = definition.key,
                processName = definition.processName ?: definition.key,
                version = definition.version,
                totalInstances = total,
                runningInstances = running,
                completedInstances = completed,
                failedInstances = failed,
                suspendedInstances = suspended,
                incidentCount = incidents,
                avgExecutionTimeMs = avgExecutionTime,
                successRate = (successRate * 100).roundToLong() / 100.0,
                lastExecutedAt = lastExecutedAt,
                createdAt = createdAt
            )
        }
    }

    /**
     * Get incidents (failed/suspended/error instances)
     */
    fun getIncidents(page: Int = 0, pageSize: Int = 20): List<IncidentDto> {
        val incidents = processInstanceRepository.findAll()
            .filter { it.status == ProcessStatus.FAILED || it.status == ProcessStatus.SUSPENDED || it.status == ProcessStatus.ERROR }
            .sortedByDescending { it.updatedAt }

        return incidents.map { instance ->
            IncidentDto(
                instanceId = instance.id!!,
                processId = instance.processDefinition.key,
                processName = instance.processDefinition.processName ?: instance.processDefinition.key,
                status = instance.status.name,
                errorMessage = null,  // TODO: add error message field to ProcessInstance
                errorType = null,      // TODO: add error type field to ProcessInstance
                currentNode = instance.currentNode?.firstOrNull(),
                createdAt = instance.createdAt.toString(),
                updatedAt = instance.updatedAt.toString(),
                nestingLevel = instance.nestingLevel,
                parentInstanceId = instance.parentInstanceId
            )
        }.drop(page * pageSize).take(pageSize)
    }

    /**
     * Phase 9.3: Get execution time trends over a period
     */
    fun getExecutionTrends(processId: String? = null, bucketSizeMinutes: Int = 60, hoursBack: Int = 24): ExecutionTrendsResponseDto {
        val now = LocalDateTime.now()
        val from = now.minusHours(hoursBack.toLong())

        val instances = if (processId != null) {
            val processDef = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
            if (processDef != null) {
                processInstanceRepository.findByProcessDefinitionId(processDef.id!!)
                    .filter { it.createdAt.isAfter(from) }
            } else {
                emptyList()
            }
        } else {
            processInstanceRepository.findAll()
                .filter { it.createdAt.isAfter(from) }
        }

        // Group by time buckets
        val bucketMap = mutableMapOf<LocalDateTime, MutableList<Any>>()

        instances.forEach { instance ->
            val bucket = instance.createdAt.truncatedTo(ChronoUnit.MINUTES)
                .minusMinutes((instance.createdAt.minute % bucketSizeMinutes).toLong())
            @Suppress("UNCHECKED_CAST")
            (bucketMap.getOrPut(bucket) { mutableListOf() } as MutableList<Any>).add(instance)
        }

        val trends = bucketMap.entries.sortedBy { it.key }.map { (bucket, instancesList) ->
            @Suppress("UNCHECKED_CAST")
            val bucketInstances = instancesList as List<com.easy.bpm.model.process.ProcessInstance>
            val executionTimes = bucketInstances
                .filter { it.status == ProcessStatus.COMPLETED || it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }
                .map { 
                    ChronoUnit.MILLIS.between(it.createdAt, it.updatedAt)
                }

            val avgTime = if (executionTimes.isNotEmpty()) executionTimes.average().roundToLong() else 0L
            val minTime = executionTimes.minOrNull() ?: 0L
            val maxTime = executionTimes.maxOrNull() ?: 0L
            val successCount = bucketInstances.count { it.status == ProcessStatus.COMPLETED }
            val failureCount = bucketInstances.count { it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }

            ExecutionTrendDto(
                timestamp = bucket.toEpochSecond(java.time.ZoneOffset.UTC) * 1000,
                averageExecutionTimeMs = avgTime,
                minExecutionTimeMs = minTime,
                maxExecutionTimeMs = maxTime,
                instanceCount = instances.size.toLong(),
                successCount = successCount.toLong(),
                failureCount = failureCount.toLong()
            )
        }

        // Calculate percentiles
        val allExecutionTimes = instances
            .filter { it.status == ProcessStatus.COMPLETED || it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }
            .map { 
                ChronoUnit.MILLIS.between(it.createdAt, it.updatedAt)
            }
            .sorted()

        val overallAvg = if (allExecutionTimes.isNotEmpty()) allExecutionTimes.average().roundToLong() else 0L
        val median = if (allExecutionTimes.isNotEmpty()) {
            allExecutionTimes[allExecutionTimes.size / 2]
        } else 0L
        val p95 = if (allExecutionTimes.isNotEmpty()) {
            allExecutionTimes[(allExecutionTimes.size * 0.95).toInt().coerceAtMost(allExecutionTimes.size - 1)]
        } else 0L
        val p99 = if (allExecutionTimes.isNotEmpty()) {
            allExecutionTimes[(allExecutionTimes.size * 0.99).toInt().coerceAtMost(allExecutionTimes.size - 1)]
        } else 0L

        return ExecutionTrendsResponseDto(
            processId = processId,
            period = "${hoursBack}h",
            trends = trends,
            overallAverageMs = overallAvg,
            overallMedianMs = median,
            p95Ms = p95,
            p99Ms = p99
        )
    }

    /**
     * Phase 9.3: Get SLA metrics for all instances
     */
    fun getSLAMetrics(): SLAMetricsResponseDto {
        val instances = processInstanceRepository.findAll()
        
        val slaMetrics = instances.map { instance ->
            val currentDurationMs = ChronoUnit.MILLIS.between(instance.createdAt, LocalDateTime.now())
            // Default SLA: 1 hour = 3600000 ms
            val targetDurationMs = 3600000L
            
            val status = when {
                instance.status == ProcessStatus.COMPLETED -> SLAStatusEnum.MET
                currentDurationMs > targetDurationMs -> SLAStatusEnum.VIOLATED
                currentDurationMs > targetDurationMs * 0.8 -> SLAStatusEnum.AT_RISK
                else -> SLAStatusEnum.MET
            }

            val percentageComplete = when (instance.status) {
                ProcessStatus.COMPLETED -> 100
                ProcessStatus.FAILED, ProcessStatus.ERROR -> 0
                else -> ((currentDurationMs.toDouble() / targetDurationMs) * 100).toInt().coerceIn(0, 99)
            }

            SLAMetricDto(
                instanceId = instance.id!!,
                processId = instance.processDefinition.key,
                processName = instance.processDefinition.processName ?: instance.processDefinition.key,
                currentNode = instance.currentNode?.firstOrNull(),
                createdAt = instance.createdAt,
                targetDurationMs = targetDurationMs,
                currentDurationMs = currentDurationMs,
                status = status,
                percentageComplete = percentageComplete
            )
        }

        val metCount = slaMetrics.count { it.status == SLAStatusEnum.MET }
        val atRiskCount = slaMetrics.count { it.status == SLAStatusEnum.AT_RISK }
        val violatedCount = slaMetrics.count { it.status == SLAStatusEnum.VIOLATED }
        val total = slaMetrics.size

        val percentage = SLAPercentageDto(
            met = if (total > 0) (metCount.toDouble() / total) * 100 else 0.0,
            atRisk = if (total > 0) (atRiskCount.toDouble() / total) * 100 else 0.0,
            violated = if (total > 0) (violatedCount.toDouble() / total) * 100 else 0.0
        )

        return SLAMetricsResponseDto(
            totalInstances = total.toLong(),
            metInstances = metCount.toLong(),
            atRiskInstances = atRiskCount.toLong(),
            violatedInstances = violatedCount.toLong(),
            metricsPercentage = percentage,
            criticalInstances = slaMetrics
                .filter { it.status == SLAStatusEnum.VIOLATED || it.status == SLAStatusEnum.AT_RISK }
                .sortedByDescending { it.currentDurationMs }
                .take(10),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Phase 9.3: Get activity feed
     */
    fun getActivityFeed(page: Int = 0, pageSize: Int = 50): ActivityFeedResponseDto {
        val instances = processInstanceRepository.findAll()
            .sortedByDescending { it.updatedAt }

        val activities = instances.flatMap { instance ->
            val items = mutableListOf<ActivityFeedItemDto>()

            items.add(ActivityFeedItemDto(
                id = (instance.id!! * 1000 + 1),
                timestamp = instance.createdAt,
                type = ActivityType.INSTANCE_CREATED,
                processId = instance.processDefinition.key,
                processName = instance.processDefinition.processName ?: instance.processDefinition.key,
                instanceId = instance.id!!,
                nodeId = null,
                nodeName = null,
                description = "Process instance created",
                severity = "INFO",
                metadata = mapOf("status" to instance.status.name)
            ))

            if (instance.status == ProcessStatus.COMPLETED) {
                items.add(ActivityFeedItemDto(
                    id = (instance.id!! * 1000 + 2),
                    timestamp = instance.updatedAt,
                    type = ActivityType.INSTANCE_COMPLETED,
                    processId = instance.processDefinition.key,
                    processName = instance.processDefinition.processName ?: instance.processDefinition.key,
                    instanceId = instance.id!!,
                    nodeId = null,
                    nodeName = null,
                    description = "Process instance completed successfully",
                    severity = "INFO",
                    metadata = mapOf("duration" to ChronoUnit.MILLIS.between(instance.createdAt, instance.updatedAt))
                ))
            } else if (instance.status == ProcessStatus.FAILED || instance.status == ProcessStatus.ERROR) {
                items.add(ActivityFeedItemDto(
                    id = (instance.id!! * 1000 + 3),
                    timestamp = instance.updatedAt,
                    type = ActivityType.INCIDENT_CREATED,
                    processId = instance.processDefinition.key,
                    processName = instance.processDefinition.processName ?: instance.processDefinition.key,
                    instanceId = instance.id!!,
                    nodeId = instance.currentNode?.firstOrNull(),
                    nodeName = instance.currentNode?.firstOrNull(),
                    description = "Process instance failed at node: ${instance.currentNode?.firstOrNull()}",
                    severity = "ERROR",
                    metadata = mapOf("currentNode" to (instance.currentNode?.firstOrNull() ?: "unknown"))
                ))
            }

            items
        }.sortedByDescending { it.timestamp }

        val totalCount = activities.size
        val paginatedActivities = activities.drop(page * pageSize).take(pageSize)

        return ActivityFeedResponseDto(
            items = paginatedActivities,
            totalCount = totalCount.toLong(),
            hasMore = (page + 1) * pageSize < totalCount,
            generatedAt = LocalDateTime.now()
        )
    }

    /**
     * Phase 9.3: Get analytics summary
     */
    fun getAnalyticsSummary(period: String = "24h"): AnalyticsSummaryDto {
        val hoursBack = when (period) {
            "7d" -> 168
            "30d" -> 720
            else -> 24 // default to 24h
        }

        val now = LocalDateTime.now()
        val from = now.minusHours(hoursBack.toLong())

        val allInstances = processInstanceRepository.findAll()
        val instances = allInstances.filter { it.createdAt.isAfter(from) }

        val completedCount = instances.count { it.status == ProcessStatus.COMPLETED }
        val failedCount = instances.count { it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }
        val successRate = if (instances.isNotEmpty()) {
            (completedCount.toDouble() / instances.size) * 100
        } else 0.0

        val executionTimes = instances
            .filter { it.status == ProcessStatus.COMPLETED || it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }
            .map { 
                ChronoUnit.MILLIS.between(it.createdAt, it.updatedAt)
            }

        val avgExecutionTime = if (executionTimes.isNotEmpty()) executionTimes.average().roundToLong() else 0L

        // Get failing processes
        val processDefs = processDefinitionRepository.findAll()
        val failingProcesses = processDefs.mapNotNull { processDef ->
            val processInstances = allInstances.filter { it.processDefinition.id == processDef.id }
            if (processInstances.isNotEmpty()) {
                val failCount = processInstances.count { it.status == ProcessStatus.FAILED || it.status == ProcessStatus.ERROR }
                val failRate = (failCount.toDouble() / processInstances.size) * 100
                ProcessFailureRateDto(
                    processId = processDef.key,
                    processName = processDef.processName ?: processDef.key,
                    totalInstances = processInstances.size.toLong(),
                    failedInstances = failCount.toLong(),
                    failureRate = failRate
                )
            } else null
        }.sortedByDescending { it.failureRate }.take(5)

        val slaMetrics = getSLAMetrics().metricsPercentage
        val activityFeed = getActivityFeed(0, 10)

        return AnalyticsSummaryDto(
            period = period,
            totalProcesses = processDefs.size.toLong(),
            totalInstances = instances.size.toLong(),
            completedInstances = completedCount.toLong(),
            failedInstances = failedCount.toLong(),
            suspendedInstances = instances.count { it.status == ProcessStatus.SUSPENDED }.toLong(),
            runningInstances = instances.count { it.status == ProcessStatus.ACTIVE }.toLong(),
            averageExecutionTimeMs = avgExecutionTime,
            successRate = successRate,
            slaMetStatus = slaMetrics,
            incidentsCount = failedCount + instances.count { it.status == ProcessStatus.ERROR }.toLong(),
            recentActivities = activityFeed.items,
            topFailingProcesses = failingProcesses
        )
    }
}
