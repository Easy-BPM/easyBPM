package com.easy.bpm.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.Counter
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Centralized metrics tracking for BPM process execution.
 * Tracks timers, counters, and gauges for observability.
 */
@Service
class MetricsService(
    private val meterRegistry: MeterRegistry
) {

    // Timers
    private val processExecutionTimer = Timer.builder("process.execution.duration")
        .description("Time to execute a process instance")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    private val nodeExecutionTimer = Timer.builder("node.execution.duration")
        .description("Time to execute a single node")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    private val taskCompletionTimer = Timer.builder("task.completion.duration")
        .description("Time from task creation to completion")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    private val taskExecutionTimer = Timer.builder("task.execution.duration")
        .description("Time to execute/complete a task")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    private val taskQueryTimer = Timer.builder("task.query.duration")
        .description("Time to query task from database")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    private val serviceTaskExecutionTimer = Timer.builder("service.task.duration")
        .description("Time to execute a service task (external call)")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    // Counters
    private val processStartedCounter = Counter.builder("process.started.total")
        .description("Total number of processes started")
        .register(meterRegistry)

    private val processCompletedCounter = Counter.builder("process.completed.total")
        .description("Total number of processes completed successfully")
        .register(meterRegistry)

    private val processFailedCounter = Counter.builder("process.failed.total")
        .description("Total number of processes that failed")
        .register(meterRegistry)

    private val taskCreatedCounter = Counter.builder("task.created.total")
        .description("Total number of tasks created")
        .register(meterRegistry)

    private val taskCompletedCounter = Counter.builder("task.completed.total")
        .description("Total number of tasks completed")
        .register(meterRegistry)

    private val serviceTaskRetryCounter = Counter.builder("service.task.retry.total")
        .description("Total number of service task retries")
        .register(meterRegistry)

    private val serviceTaskDlqCounter = Counter.builder("service.task.dlq.total")
        .description("Total number of service tasks routed to DLQ")
        .register(meterRegistry)

    private val messageEventCounter = Counter.builder("message.event.received.total")
        .description("Total number of message events received")
        .register(meterRegistry)

    // ==================== Process Metrics ====================

    fun recordProcessExecution(durationMillis: Long) {
        processExecutionTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }

    fun recordProcessStarted() {
        processStartedCounter.increment()
    }

    fun recordProcessCompleted() {
        processCompletedCounter.increment()
    }

    fun recordProcessFailed() {
        processFailedCounter.increment()
    }

    // ==================== Node Metrics ====================

    fun recordNodeExecution(durationMillis: Long, nodeType: String) {
        Timer.builder("node.execution.duration")
            .tag("nodeType", nodeType)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(durationMillis, TimeUnit.MILLISECONDS)
    }

    // ==================== Task Metrics ====================

    fun recordTaskCreated(nodeId: String) {
        taskCreatedCounter.increment()
        meterRegistry.gauge("task.active", taskCreatedCounter.count())
    }

    fun recordTaskCompleted() {
        taskCompletedCounter.increment()
    }

    fun recordTaskExecution(durationMillis: Long) {
        taskExecutionTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }

    fun recordTaskQueryDuration(durationMillis: Long) {
        taskQueryTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }

    // ==================== Service Task Metrics ====================

    fun recordServiceTaskExecution(durationMillis: Long, success: Boolean) {
        Timer.builder("service.task.duration")
            .tag("status", if (success) "success" else "failure")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(durationMillis, TimeUnit.MILLISECONDS)

        serviceTaskExecutionTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }

    fun recordServiceTaskRetry(retryCount: Int) {
        serviceTaskRetryCounter.increment()
        meterRegistry.gauge("service.task.retry.count", retryCount.toDouble())
    }

    fun recordServiceTaskDLQ() {
        serviceTaskDlqCounter.increment()
    }

    // ==================== Message Metrics ====================

    fun recordMessageEventReceived(messageName: String) {
        messageEventCounter.increment()
        meterRegistry.counter("message.event.received.total", "messageName", messageName).increment()
    }

    // ==================== Health Metrics ====================

    fun recordDatabaseConnection(available: Boolean) {
        meterRegistry.gauge("database.available", if (available) 1.0 else 0.0)
    }

    fun recordRabbitMQConnection(available: Boolean) {
        meterRegistry.gauge("rabbitmq.available", if (available) 1.0 else 0.0)
    }

    fun recordProcessVariableCount(processInstanceId: Long, count: Int) {
        meterRegistry.gauge("process.variables", count.toDouble())
    }

    fun recordActiveProcesses(count: Int) {
        meterRegistry.gauge("process.active", count.toDouble())
    }
}

