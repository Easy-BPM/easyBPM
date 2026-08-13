package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.handler.ProcessUserTaskHandler
import com.easy.bpm.service.variable.HistoricVariableArchiver
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class ProcessInstanceLifecycleManagerTest : FunSpec() {
    init {
        val processInstanceRepository = mockk<ProcessInstanceRepository>()
        val processVariableRepository = mockk<ProcessVariableRepository>()
        val taskVariableRepository = mockk<TaskVariableRepository>()
        val taskRepository = mockk<TaskRepository>()
        val workerRequestRepository = mockk<WorkerRequestRepository>()
        val objectMapper = ObjectMapper()
        val messageSubscriptionService = mockk<MessageSubscriptionService>()
        val metricsService = mockk<MetricsService>(relaxed = true)
        val timelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
        val userTaskHandler = mockk<ProcessUserTaskHandler>()
        val historicVariableArchiver = mockk<HistoricVariableArchiver>(relaxed = true)
        val manager = ProcessInstanceLifecycleManager(
            processInstanceRepository,
            processVariableRepository,
            taskVariableRepository,
            taskRepository,
            workerRequestRepository,
            objectMapper,
            messageSubscriptionService,
            metricsService,
            timelineService,
            userTaskHandler,
            historicVariableArchiver
        )

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should clean source task and create target user task on manual move") {
            val definitionJson = unitTestBpmnXml(
                "approval",
                """[
                    {"id": "review", "type": "HumanTask", "name": "Review"},
                    {"id": "approve", "type": "HumanTask", "name": "Approve"}
                ]"""
            )
            val definition = ProcessDefinition(id = 1, key = "approval", definitionJson = definitionJson)
            val instance = ProcessInstance(
                id = 10,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("review"),
                nodeHistory = listOf("review")
            )
            val pendingTask = Task(id = 99, processInstanceId = 10, title = "Review", nodeId = "review")

            every { processInstanceRepository.findByIdForUpdate(10) } returns instance
            every {
                taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(10, "review", TaskStatus.PENDING)
            } returns listOf(pendingTask)
            every {
                taskRepository.findByProcessInstanceIdAndNodeIdAndStatus(10, "approve", TaskStatus.PENDING)
            } returns emptyList()
            justRun { taskRepository.delete(pendingTask) }
            justRun { userTaskHandler.handleUserTask(instance, any()) }
            every { processInstanceRepository.save(instance) } returns instance

            val result = manager.moveProcessNode(10, "review", "approve")

            result.currentNode shouldBe listOf("approve")
            result.nodeHistory shouldContain "approve"
            verify {
                historicVariableArchiver.archiveTaskVariables(pendingTask)
                taskRepository.delete(pendingTask)
                userTaskHandler.handleUserTask(instance, any())
            }
        }

        test("should delete process instance dependencies before deleting instance") {
            val definition = ProcessDefinition(id = 1, key = "cleanup", definitionJson = unitTestBpmnXml("cleanup", "[]"))
            val instance = ProcessInstance(id = 10, processDefinition = definition, status = ProcessStatus.CANCELLED)
            val task = Task(id = 22, processInstanceId = 10, nodeId = "task")

            every { processInstanceRepository.findById(10) } returns Optional.of(instance)
            every { taskRepository.findByProcessInstanceId(10) } returns listOf(task)
            every { taskVariableRepository.deleteByTaskId(22) } returns 1
            every { taskRepository.deleteByProcessInstanceId(10) } returns 1
            every { processVariableRepository.deleteByProcessInstanceId(10) } returns 1
            justRun { messageSubscriptionService.deleteSubscriptionsForInstance(10) }
            every { workerRequestRepository.deleteByProcessInstanceId(10) } returns 1
            justRun { processInstanceRepository.delete(instance) }

            manager.deleteProcessInstance(10)

            verify {
                taskVariableRepository.deleteByTaskId(22)
                taskRepository.deleteByProcessInstanceId(10)
                processVariableRepository.deleteByProcessInstanceId(10)
                messageSubscriptionService.deleteSubscriptionsForInstance(10)
                workerRequestRepository.deleteByProcessInstanceId(10)
                processInstanceRepository.delete(instance)
            }
        }
    }
}
