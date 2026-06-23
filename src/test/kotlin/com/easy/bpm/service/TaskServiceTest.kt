package com.easy.bpm.service

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
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
    val mockIntegrationService = mockk<IntegrationService>()
    val mockFormService = mockk<FormService>()
    val mockObjectMapper = mockk<ObjectMapper>()
    val mockRabbitPublisher = mockk<com.easy.bpm.messaging.RabbitPublisher>(relaxed = true)
    val mockGatewayService = mockk<GatewayService>()
    val mockMessageSubscriptionService = mockk<MessageSubscriptionService>()
    val mockMetricsService = mockk<MetricsService>(relaxed = true)
    val mockTimelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)

    val taskService = TaskService(
        mockTaskRepository,
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockTaskVariableRepository,
        mockIntegrationService,
        mockFormService,
        mockObjectMapper,
        mockRabbitPublisher,
        mockGatewayService,
        mockMessageSubscriptionService,
        mockMetricsService,
        mockTimelineService
    )

    val objectMapper = ObjectMapper()

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
