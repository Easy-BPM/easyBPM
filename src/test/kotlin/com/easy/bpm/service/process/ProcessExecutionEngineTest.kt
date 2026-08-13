package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.handler.CodeTaskHandler
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.handler.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class ProcessExecutionEngineTest : FunSpec() {
    init {
        val metricsService = mockk<MetricsService>(relaxed = true)
        val failureHandler = mockk<ProcessFailureHandler>()
        val navigator = mockk<ProcessNavigator>()
        val messageNodeHandler = mockk<ProcessMessageNodeHandler>()
        val variableManager = mockk<ProcessVariableManager>()
        val userTaskHandler = mockk<ProcessUserTaskHandler>()
        val serviceTaskHandler = mockk<ProcessServiceTaskHandler>()
        val processAiTaskHandler = mockk<ProcessAiTaskHandler>()
        val processAgentCallHandler = mockk<ProcessAgentCallHandler>()
        val codeTaskHandler = mockk<CodeTaskHandler>()
        val callActivityHandler = mockk<CallActivityHandler>()
        val timelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
        val lifecycleManager = mockk<ProcessInstanceLifecycleManager>()
        val engine = ProcessExecutionEngine(
            metricsService,
            failureHandler,
            navigator,
            messageNodeHandler,
            variableManager,
            userTaskHandler,
            serviceTaskHandler,
            processAiTaskHandler,
            processAgentCallHandler,
            codeTaskHandler,
            callActivityHandler,
            timelineService,
            lifecycleManager
        )
        val objectMapper = ObjectMapper()

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should route node failures through attached error boundary") {
            val definition = objectMapper.readTree(
                """
                {
                  "nodes": [
                    {"id": "api", "type": "APITask"},
                    {"id": "api-boundary", "type": "ErrorBoundaryEvent", "attachedTo": "api",
                      "config": {"errorCode": "API_ERROR", "exceptionVariable": "lastError"}},
                    {"id": "recovery", "type": "HumanTask"}
                  ],
                  "flows": []
                }
                """.trimIndent()
            )
            val apiNode = definition.get("nodes")[0]
            val boundaryNode = definition.get("nodes")[1]
            val processDefinition = ProcessDefinition(id = 1, key = "errors", definitionJson = definition.toString())
            val instance = ProcessInstance(
                id = 42,
                processDefinition = processDefinition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("api")
            )

            every { serviceTaskHandler.handleApiTask(instance, apiNode) } throws RuntimeException("boom")
            every { navigator.findAttachedErrorBoundary(apiNode, definition) } returns boundaryNode
            every { variableManager.assignProcessVariables(42, mapOf("lastError" to "boom")) } returns emptyList()
            every { navigator.getNextNodes(boundaryNode, definition, instance) } returns listOf("recovery")
            justRun { navigator.advanceProcess(instance, listOf("recovery"), definition) }
            justRun { userTaskHandler.handleUserTask(instance, definition.get("nodes")[2]) }

            engine.executeNodes(listOf("api"), instance, definition)

            verify {
                variableManager.assignProcessVariables(42, mapOf("lastError" to "boom"))
                navigator.advanceProcess(instance, listOf("recovery"), definition)
                userTaskHandler.handleUserTask(instance, definition.get("nodes")[2])
            }
            verify(exactly = 0) { failureHandler.failInstance(any(), any(), any(), any(), any(), any()) }
        }

        test("should execute code task assign outputs and continue") {
            val definition = objectMapper.readTree(
                """
                {
                  "nodes": [
                    {
                      "id": "code",
                      "type": "CodeTask",
                      "config": {
                        "jarId": 1,
                        "className": "TestService",
                        "methodName": "processOrder",
                        "inputs": [
                          {"targetName": "0", "source": "variable", "value": "orderId"},
                          {"targetName": "1", "source": "variable", "value": "amount"}
                        ],
                        "outputs": [
                          {"sourceName": "returnValue", "value": "processedOrderMessage"}
                        ]
                      }
                    },
                    {"id": "review", "type": "HumanTask"}
                  ],
                  "flows": [
                    {"from": "code", "to": "review", "condition": null}
                  ]
                }
                """.trimIndent()
            )
            val codeNode = definition.get("nodes")[0]
            val reviewNode = definition.get("nodes")[1]
            val processDefinition = ProcessDefinition(id = 1, key = "code-task", definitionJson = definition.toString())
            val instance = ProcessInstance(
                id = 43,
                processDefinition = processDefinition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("code")
            )

            every { variableManager.getProcessVariablesAsMap(43) } returns mapOf(
                "orderId" to "ORDER-1001",
                "amount" to 1250
            )
            every {
                codeTaskHandler.executeCodeTask(
                    instanceId = 43,
                    nodeId = "code",
                    jarId = 1,
                    className = "TestService",
                    methodName = "processOrder",
                    inputMappings = mapOf("orderId" to "0", "amount" to "1"),
                    outputMappings = mapOf("returnValue" to "processedOrderMessage"),
                    inputVariables = mapOf("orderId" to "ORDER-1001", "amount" to 1250)
                )
            } returns mapOf("processedOrderMessage" to "Processed ORDER-1001")
            every { variableManager.assignProcessVariables(43, mapOf("processedOrderMessage" to "Processed ORDER-1001")) } returns emptyList()
            every { navigator.getNextNodes(codeNode, definition, instance) } returns listOf("review")
            justRun { navigator.advanceProcess(instance, listOf("review"), definition) }
            justRun { userTaskHandler.handleUserTask(instance, reviewNode) }

            engine.executeNodes(listOf("code"), instance, definition)

            verify {
                codeTaskHandler.executeCodeTask(
                    instanceId = 43,
                    nodeId = "code",
                    jarId = 1,
                    className = "TestService",
                    methodName = "processOrder",
                    inputMappings = mapOf("orderId" to "0", "amount" to "1"),
                    outputMappings = mapOf("returnValue" to "processedOrderMessage"),
                    inputVariables = mapOf("orderId" to "ORDER-1001", "amount" to 1250)
                )
                variableManager.assignProcessVariables(43, mapOf("processedOrderMessage" to "Processed ORDER-1001"))
                navigator.advanceProcess(instance, listOf("review"), definition)
                userTaskHandler.handleUserTask(instance, reviewNode)
            }
        }
    }
}
