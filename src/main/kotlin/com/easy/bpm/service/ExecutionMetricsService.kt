package com.easy.bpm.service

import com.easy.bpm.dto.ExecutionMetricsDto
import com.easy.bpm.dto.ProcessMetricsDto
import com.easy.bpm.dto.ExecutionTimeStatsDto
import com.easy.bpm.dto.TrendDataPoint
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
}
