package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.service.process.handler.MessageReceivedResult
import com.easy.bpm.service.process.handler.ProcessMessageReceivedHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence

class ProcessMessageRuntimeServiceTest : FunSpec() {
    init {
        val processInstanceRepository = mockk<ProcessInstanceRepository>()
        val objectMapper = ObjectMapper()
        val navigator = mockk<ProcessNavigator>()
        val executionEngine = mockk<ProcessExecutionEngine>()
        val messageReceivedHandler = mockk<ProcessMessageReceivedHandler>()
        val messageStartResolver = mockk<ProcessMessageStartResolver>()
        val timelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
        val instanceStarter = mockk<ProcessInstanceStarter>()
        val service = ProcessMessageRuntimeService(
            processInstanceRepository,
            objectMapper,
            navigator,
            executionEngine,
            messageReceivedHandler,
            messageStartResolver,
            timelineService,
            instanceStarter
        )

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should continue process when a waiting message subscription is found") {
            val definition = objectMapper.readTree("""{"nodes": []}""")
            val instance = processInstance(definition.toString())
            val nextNodes = listOf("after-message")
            val result = MessageReceivedResult(
                subscriptionFound = true,
                instance = instance,
                definition = definition,
                nextNodes = nextNodes
            )

            every { messageReceivedHandler.handleReceived("ApprovalReceived", "order-1", mapOf("approved" to true)) } returns result
            justRun { navigator.advanceProcess(instance, nextNodes, definition) }
            justRun { executionEngine.executeNodes(nextNodes, instance, definition) }

            service.handleMessageReceived("ApprovalReceived", "order-1", mapOf("approved" to true))

            verifySequence {
                messageReceivedHandler.handleReceived("ApprovalReceived", "order-1", mapOf("approved" to true))
                navigator.advanceProcess(instance, nextNodes, definition)
                executionEngine.executeNodes(nextNodes, instance, definition)
            }
            verify(exactly = 0) { messageStartResolver.findMatch(any(), any(), any()) }
        }

        test("should start process when no subscription exists and message start matches") {
            val definitionJson = unitTestBpmnXml(
                "message-start",
                """[
                    {"id": "message-start", "type": "MessageStartEvent"}
                ]"""
            )
            val definition = ProcessDefinition(id = 5, key = "message-start", definitionJson = definitionJson)
            val startNode = objectMapper.readTree("""{"id": "message-start", "type": "MessageStartEvent"}""")
            val variables = mapOf("customerId" to "c-1")
            val matchVariables = mapOf("customerId" to "c-1", "correlationKey" to "c-1")
            val startedInstance = ProcessInstance(
                id = 44,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("message-start")
            )

            every { messageReceivedHandler.handleReceived("CustomerCreated", "c-1", variables) } returns MessageReceivedResult(
                subscriptionFound = false
            )
            every { messageStartResolver.findMatch("CustomerCreated", "c-1", variables) } returns MessageStartMatch(
                definition = definition,
                startNode = startNode,
                variables = matchVariables
            )
            every { instanceStarter.startWithDefinition(definition, matchVariables, "message-start") } returns startedInstance

            service.handleMessageReceived("CustomerCreated", "c-1", variables)

            verify {
                timelineService.record(
                    processInstanceId = 44,
                    nodeId = "message-start",
                    eventType = ProcessInstanceEventType.MESSAGE_RECEIVED,
                    message = "Message 'CustomerCreated' started process with correlation key 'c-1'."
                )
            }
            verify(exactly = 0) { navigator.advanceProcess(any(), any(), any()) }
            verify(exactly = 0) { executionEngine.executeNodes(any(), any(), any()) }
        }

        test("should advance process when timer timeout targets a timer node") {
            val definitionJson = unitTestBpmnXml(
                "message-process",
                """[
                    {"id": "timer", "type": "TimerEvent"},
                    {"id": "next", "type": "HumanTask"}
                ]"""
            )
            val instance = processInstance(definitionJson)
            val definition = internalJsonFromBpmn(definitionJson, objectMapper)
            val timerNode = definition.get("nodes")[0]

            every { processInstanceRepository.findByIdForUpdate(10) } returns instance
            every { navigator.getNextNodes(timerNode, any(), instance) } returns listOf("next")
            justRun { navigator.advanceProcess(instance, listOf("next"), any()) }
            justRun { executionEngine.executeNodes(listOf("next"), instance, any()) }

            val handled = service.handleTimerTimeout(10, "timer")

            handled shouldBe true
            verify {
                navigator.advanceProcess(instance, listOf("next"), any())
                executionEngine.executeNodes(listOf("next"), instance, any())
            }
        }
    }

    private fun processInstance(definitionJson: String): ProcessInstance {
        val definition = ProcessDefinition(id = 1, key = "message-process", definitionJson = definitionJson)
        return ProcessInstance(
            id = 10,
            processDefinition = definition,
            status = ProcessStatus.ACTIVE,
            currentNode = listOf("message")
        )
    }
}
