package com.easy.bpm.service.process

import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.handler.ProcessWorkerCallbackHandler
import com.easy.bpm.service.process.handler.WorkerCallbackResult
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class ProcessWorkerCallbackService(
    private val metricsService: MetricsService,
    private val navigator: ProcessNavigator,
    private val executionEngine: ProcessExecutionEngine,
    private val workerCallbackHandler: ProcessWorkerCallbackHandler
) {
    @Transactional
    fun handleServiceTaskCompleted(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        val result = workerCallbackHandler.handleCompleted(processInstanceId, nodeId, outputs) ?: return
        continueFromWorkerCallback(result, nodeId)
    }

    @Transactional
    fun handleServiceTaskFailed(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String? = null,
        incidentSource: IncidentSource = IncidentSource.WORKER,
        externalReferenceId: String? = null
    ) {
        val result = workerCallbackHandler.handleFailed(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage ?: "Service task failed",
            incidentSource = incidentSource,
            externalReferenceId = externalReferenceId
        ) ?: return
        continueFromWorkerCallback(result, nodeId)
    }

    fun markServiceTaskTimedOut(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String,
        externalReferenceId: String? = null
    ) {
        handleServiceTaskFailed(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage,
            incidentSource = IncidentSource.WORKER,
            externalReferenceId = externalReferenceId
        )
    }

    private fun continueFromWorkerCallback(result: WorkerCallbackResult, nodeId: String) {
        if (result.shouldContinue) {
            navigator.advanceProcess(result.instance, result.nextNodes, result.definition, nodeId)
            executionEngine.executeNodes(result.nextNodes, result.instance, result.definition)
        }

        val duration = System.currentTimeMillis() - result.startTime
        metricsService.recordServiceTaskExecution(duration, success = result.success)
    }
}
