package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class ProcessInstanceStarterTest : FunSpec() {
    init {
        val repository = mockk<ProcessInstanceRepository>()
        val objectMapper = ObjectMapper()
        val metricsService = mockk<MetricsService>(relaxed = true)
        val timelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
        val variableManager = mockk<ProcessVariableManager>()
        val navigator = mockk<ProcessNavigator>()
        val executionEngine = mockk<ProcessExecutionEngine>()
        val starter = ProcessInstanceStarter(
            repository,
            objectMapper,
            metricsService,
            timelineService,
            variableManager,
            navigator,
            executionEngine
        )

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should create active instance and execute start nodes") {
            val definitionJson = """
                {
                  "processId": "simple",
                  "nodes": [
                    {"id": "start", "type": "StartEvent", "next": ["task"]},
                    {"id": "task", "type": "HumanTask"}
                  ],
                  "flows": []
                }
            """.trimIndent()
            val definition = ProcessDefinition(id = 1, key = "simple", processName = "Simple", definitionJson = definitionJson)
            val savedInstance = ProcessInstance(
                id = 50,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )

            every { repository.save(any<ProcessInstance>()) } returns savedInstance
            justRun { variableManager.initializeProcessVariables(savedInstance, any()) }
            every { navigator.getStartNodes(savedInstance, any(), null) } returns listOf("task")
            justRun { executionEngine.executeNodes(listOf("task"), savedInstance, any()) }

            val result = starter.startWithDefinition(definition)

            result.status shouldBe ProcessStatus.ACTIVE
            result.currentNode shouldBe listOf("task")
            result.nodeHistory shouldContain "task"
            verify {
                timelineService.record(
                    processInstanceId = 50,
                    eventType = ProcessInstanceEventType.PROCESS_STARTED,
                    message = "Process instance started."
                )
                executionEngine.executeNodes(listOf("task"), savedInstance, any())
            }
        }
    }
}
