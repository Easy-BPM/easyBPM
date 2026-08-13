package com.easy.bpm.service.task

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.handler.AgentProcessCallHandler
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.HistoricTaskVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.integration.IntegrationService
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.GatewayService
import com.easy.bpm.service.process.unitTestBpmnXml
import com.easy.bpm.service.process.ProcessInstanceTimelineService
import com.easy.bpm.service.process.handler.ProcessFailureHandler
import com.easy.bpm.service.variable.HistoricVariableArchiver
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.util.*

class TaskServiceTest : FunSpec() {
    init {
    val mockTaskRepository = mockk<TaskRepository>()
    val mockProcessInstanceRepository = mockk<ProcessInstanceRepository>()
    val mockProcessVariableRepository = mockk<ProcessVariableRepository>()
    val mockTaskVariableRepository = mockk<TaskVariableRepository>()
    val mockHistoricTaskVariableRepository = mockk<HistoricTaskVariableRepository>()
    val mockIntegrationService = mockk<IntegrationService>()
    val mockFormService = mockk<FormService>()
    val mockObjectMapper = mockk<ObjectMapper>()
    val mockRabbitPublisher = mockk<com.easy.bpm.messaging.RabbitPublisher>(relaxed = true)
    val mockGatewayService = mockk<GatewayService>()
    val mockMessageSubscriptionService = mockk<MessageSubscriptionService>()
    val mockMetricsService = mockk<MetricsService>(relaxed = true)
    val mockAgentProcessCallHandler = mockk<AgentProcessCallHandler>()
    val mockTimelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
    val mockFailureHandler = mockk<ProcessFailureHandler>(relaxed = true)
    val mockHistoricVariableArchiver = mockk<HistoricVariableArchiver>(relaxed = true)

    val taskService = TaskService(
        mockTaskRepository,
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockTaskVariableRepository,
        mockHistoricTaskVariableRepository,
        mockIntegrationService,
        mockFormService,
        mockObjectMapper,
        mockRabbitPublisher,
        mockGatewayService,
        mockMessageSubscriptionService,
        mockMetricsService,
        mockAgentProcessCallHandler,
        mockTimelineService,
        mockFailureHandler,
        mockHistoricVariableArchiver
    )

    beforeEach {
        clearAllMocks()
    }

    context("getTasks") {
        test("should retrieve paginated tasks") {
            // Arrange
            val task1 = Task(
                id = 1,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = TaskStatus.PENDING,
                formId = 1
            )
            val task2 = Task(
                id = 2,
                processInstanceId = 101,
                title = "Approve Request",
                nodeId = "task-2",
                status = TaskStatus.PENDING,
                formId = 2
            )

            val page: Page<Task> = PageImpl(listOf(task1, task2), PageRequest.of(0, 10), 2)
            every { mockTaskRepository.findAll(any<Pageable>()) } returns page

            // Act
            val result = taskService.getTasks(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.totalElements shouldBe 2
            result.content[0].title shouldBe "Review Document"
            verify { mockTaskRepository.findAll(any<Pageable>()) }
            verify { mockMetricsService.recordTaskQueryDuration(any()) }
        }

        test("should return empty page when no tasks exist") {
            // Arrange
            val emptyPage: Page<Task> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            every { mockTaskRepository.findAll(any<Pageable>()) } returns emptyPage

            // Act
            val result = taskService.getTasks(PageRequest.of(0, 10))

            // Assert
            result.content.shouldBeEmpty()
            result.totalElements shouldBe 0
        }

        test("should ignore unsupported sort fields for getTasks") {
            // Arrange
            val page: Page<Task> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            val capturedPageable = slot<Pageable>()
            every { mockTaskRepository.findAll(capture(capturedPageable)) } returns page

            // Act
            taskService.getTasks(PageRequest.of(0, 10, Sort.by("string").ascending()))

            // Assert
            capturedPageable.captured.sort.getOrderFor("string") shouldBe null
            capturedPageable.captured.sort.getOrderFor("createdAt")?.direction shouldBe Sort.Direction.DESC
            capturedPageable.captured.sort.getOrderFor("id")?.direction shouldBe Sort.Direction.DESC
        }
    }

    context("getTaskById") {
        test("should return task when it exists") {
            // Arrange
            val taskId = 1L
            val task = Task(
                id = taskId,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = TaskStatus.PENDING,
                formId = 1
            )
            every { mockTaskRepository.findById(taskId) } returns Optional.of(task)

            // Act
            val result = taskService.getTaskById(taskId)

            // Assert
            result shouldNotBe null
            result?.id shouldBe taskId
            result?.title shouldBe "Review Document"
            verify { mockMetricsService.recordTaskQueryDuration(any()) }
        }

        test("should return null when task does not exist") {
            // Arrange
            val taskId = 999L
            every { mockTaskRepository.findById(taskId) } returns Optional.empty()

            // Act
            val result = taskService.getTaskById(taskId)

            // Assert
            result shouldBe null
        }
    }

    context("completeTask") {
        test("should synchronize submitted variables to process variables when task has no form") {
            // Arrange
            val realObjectMapper = ObjectMapper()
            val service = TaskService(
                mockTaskRepository,
                mockProcessInstanceRepository,
                mockProcessVariableRepository,
                mockTaskVariableRepository,
                mockHistoricTaskVariableRepository,
                mockIntegrationService,
                mockFormService,
                realObjectMapper,
                mockRabbitPublisher,
                mockGatewayService,
                mockMessageSubscriptionService,
                mockMetricsService,
                mockAgentProcessCallHandler,
                mockTimelineService,
                mockFailureHandler,
                mockHistoricVariableArchiver
            )
            val definitionJson = unitTestBpmnXml(
                "no-form",
                """[
                    {"id": "review", "type": "UserTask", "name": "Review"},
                    {"id": "end", "type": "EndEvent", "name": "End"}
                ]"""
            )
            val definition = ProcessDefinition(id = 1, key = "no-form", definitionJson = definitionJson)
            val instance = ProcessInstance(
                id = 100,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("review"),
                nodeHistory = listOf("review")
            )
            val task = Task(
                id = 10,
                processInstanceId = 100,
                title = "Review",
                nodeId = "review",
                assignee = "alice",
                status = TaskStatus.PENDING,
                formId = null
            )

            every { mockTaskRepository.findByIdForUpdate(10) } returns Optional.of(task)
            every { mockProcessInstanceRepository.findByIdForUpdate(100) } returns instance
            every { mockTaskVariableRepository.findAllByTaskIdAndNameOrderByIdDesc(10, "decision") } returns emptyList()
            every { mockTaskVariableRepository.findAllByTaskIdAndNameOrderByIdDesc(10, "amount") } returns emptyList()
            every { mockTaskVariableRepository.save(any<TaskVariable>()) } answers { firstArg() }
            every { mockProcessVariableRepository.findByProcessInstanceIdAndName(100, "decision") } returns null
            every { mockProcessVariableRepository.findByProcessInstanceIdAndName(100, "amount") } returns null
            every { mockProcessVariableRepository.save(any<ProcessVariable>()) } answers { firstArg() }
            every { mockGatewayService.getNextNodes(any(), any(), instance) } returns listOf("end")
            every { mockTaskRepository.save(any<Task>()) } answers { firstArg() }
            every { mockProcessInstanceRepository.save(any<ProcessInstance>()) } answers { firstArg() }

            // Act
            service.completeTask(
                taskId = 10,
                assignee = "alice",
                variables = mapOf(
                    "decision" to "approved",
                    "amount" to 125
                )
            )

            // Assert
            verify {
                mockTaskVariableRepository.save(match<TaskVariable> {
                    it.taskId == 10L && it.processInstanceId == 100L && it.name == "decision" && it.value.asText() == "approved"
                })
                mockTaskVariableRepository.save(match<TaskVariable> {
                    it.taskId == 10L && it.processInstanceId == 100L && it.name == "amount" && it.value.asInt() == 125
                })
                mockProcessVariableRepository.save(match<ProcessVariable> {
                    it.processInstanceId == 100L && it.name == "decision" && it.value.asText() == "approved"
                })
                mockProcessVariableRepository.save(match<ProcessVariable> {
                    it.processInstanceId == 100L && it.name == "amount" && it.value.asInt() == 125
                })
            }
        }
    }

    context("searchTasks") {
        test("should search tasks by assignee") {
            // Arrange
            val assignee = "alice@example.com"
            val task = Task(
                id = 1,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = TaskStatus.PENDING,
                assignee = assignee,
                formId = 1
            )

            val page: Page<Task> = PageImpl(listOf(task), PageRequest.of(0, 10), 1)
            every { mockTaskRepository.findByAssignee(assignee, any<Pageable>()) } returns page

            // Act
            val result = taskService.searchTasks(assignee = assignee, status = null, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].assignee shouldBe assignee
            verify { mockTaskRepository.findByAssignee(assignee, any<Pageable>()) }
        }

        test("should search tasks by status") {
            // Arrange
            val status = TaskStatus.PENDING
            val task = Task(
                id = 1,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = status,
                formId = 1
            )

            val page: Page<Task> = PageImpl(listOf(task), PageRequest.of(0, 10), 1)
            every { mockTaskRepository.findByStatus(status, any<Pageable>()) } returns page

            // Act
            val result = taskService.searchTasks(assignee = null, status = status, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].status shouldBe status
            verify { mockTaskRepository.findByStatus(status, any<Pageable>()) }
        }

        test("should search tasks by assignee and status") {
            // Arrange
            val assignee = "alice@example.com"
            val status = TaskStatus.COMPLETED
            val task = Task(
                id = 1,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = status,
                assignee = assignee,
                formId = 1
            )

            val page: Page<Task> = PageImpl(listOf(task), PageRequest.of(0, 10), 1)
            every { mockTaskRepository.findByAssigneeAndStatus(assignee, status, any<Pageable>()) } returns page

            // Act
            val result = taskService.searchTasks(assignee = assignee, status = status, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].assignee shouldBe assignee
            result.content[0].status shouldBe status
            verify { mockTaskRepository.findByAssigneeAndStatus(assignee, status, any<Pageable>()) }
        }

        test("should return all tasks when no search criteria provided") {
            // Arrange
            val task = Task(
                id = 1,
                processInstanceId = 100,
                title = "Review Document",
                nodeId = "task-1",
                status = TaskStatus.PENDING,
                formId = 1
            )

            val page: Page<Task> = PageImpl(listOf(task), PageRequest.of(0, 10), 1)
            every { mockTaskRepository.findAll(any<Pageable>()) } returns page

            // Act
            val result = taskService.searchTasks(assignee = null, status = null, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            verify { mockTaskRepository.findAll(any<Pageable>()) }
        }

        test("should ignore unsupported sort fields for searchTasks") {
            // Arrange
            val status = TaskStatus.PENDING
            val page: Page<Task> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            val capturedPageable = slot<Pageable>()
            every { mockTaskRepository.findByStatus(status, capture(capturedPageable)) } returns page

            // Act
            taskService.searchTasks(assignee = null, status = status, pageable = PageRequest.of(0, 10, Sort.by("string").ascending()))

            // Assert
            capturedPageable.captured.sort.getOrderFor("string") shouldBe null
            capturedPageable.captured.sort.getOrderFor("createdAt")?.direction shouldBe Sort.Direction.DESC
            capturedPageable.captured.sort.getOrderFor("id")?.direction shouldBe Sort.Direction.DESC
        }
    }


    }
}
