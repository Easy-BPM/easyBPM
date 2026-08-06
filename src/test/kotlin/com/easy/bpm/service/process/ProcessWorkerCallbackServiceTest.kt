package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.metrics.MetricsService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence

class ProcessWorkerCallbackServiceTest : FunSpec() {
    init {
        val metricsService = mockk<MetricsService>(relaxed = true)
        val navigator = mockk<ProcessNavigator>()
        val executionEngine = mockk<ProcessExecutionEngine>()
        val workerCallbackHandler = mockk<ProcessWorkerCallbackHandler>()
        val service = ProcessWorkerCallbackService(
            metricsService,
            navigator,
            executionEngine,
            workerCallbackHandler
        )
        val objectMapper = ObjectMapper()

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should continue process after completed worker callback") {
            val definition = objectMapper.readTree("""{"nodes": []}""")
            val instance = processInstance(definition.toString())
            val nextNodes = listOf("after-worker")
            val result = WorkerCallbackResult(
                instance = instance,
                definition = definition,
                nextNodes = nextNodes,
                startTime = System.currentTimeMillis() - 25,
                success = true
            )

            every { workerCallbackHandler.handleCompleted(10, "worker", mapOf("approved" to "true")) } returns result
            justRun { navigator.advanceProcess(instance, nextNodes, definition) }
            justRun { executionEngine.executeNodes(nextNodes, instance, definition) }

            service.handleServiceTaskCompleted(10, "worker", mapOf("approved" to "true"))

            verifySequence {
                workerCallbackHandler.handleCompleted(10, "worker", mapOf("approved" to "true"))
                navigator.advanceProcess(instance, nextNodes, definition)
                executionEngine.executeNodes(nextNodes, instance, definition)
            }
            verify { metricsService.recordServiceTaskExecution(any(), success = true) }
        }

        test("should record failed worker callback without continuing when handler stops execution") {
            val definition = objectMapper.readTree("""{"nodes": []}""")
            val instance = processInstance(definition.toString())
            val result = WorkerCallbackResult(
                instance = instance,
                definition = definition,
                nextNodes = emptyList(),
                startTime = System.currentTimeMillis() - 25,
                success = false,
                shouldContinue = false
            )

            every {
                workerCallbackHandler.handleFailed(
                    processInstanceId = 10,
                    nodeId = "worker",
                    errorMessage = "Queue timed out",
                    incidentSource = IncidentSource.WORKER,
                    externalReferenceId = "job-1"
                )
            } returns result

            service.handleServiceTaskFailed(
                processInstanceId = 10,
                nodeId = "worker",
                errorMessage = "Queue timed out",
                externalReferenceId = "job-1"
            )

            verify(exactly = 0) { navigator.advanceProcess(any(), any(), any()) }
            verify(exactly = 0) { executionEngine.executeNodes(any(), any(), any()) }
            verify { metricsService.recordServiceTaskExecution(any(), success = false) }
        }
    }

    private fun processInstance(definitionJson: String): ProcessInstance {
        val definition = ProcessDefinition(id = 1, key = "worker-process", definitionJson = definitionJson)
        return ProcessInstance(
            id = 10,
            processDefinition = definition,
            status = ProcessStatus.ACTIVE,
            currentNode = listOf("worker")
        )
    }
}
