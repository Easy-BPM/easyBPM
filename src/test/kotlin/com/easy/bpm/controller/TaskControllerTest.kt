package com.easy.bpm.controller

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.service.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus

class TaskControllerTest : FunSpec({
    val mockTaskService = mockk<TaskService>()
    val objectMapper = ObjectMapper()

    val taskController = TaskController(mockTaskService, objectMapper)

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
                status = TaskStatus.COMPLETED,
                assignee = "alice@example.com",
                formId = 2
            )

            val page: Page<Task> = PageImpl(listOf(task1, task2), PageRequest.of(0, 10), 2)
            every { mockTaskService.getTasks(any()) } returns page

            // Act
            val result = taskController.getTasks(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.totalElements shouldBe 2
            verify { mockTaskService.getTasks(any()) }
        }
    }

    context("getTaskById") {
        test("should return task when found") {
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
            every { mockTaskService.getTaskById(taskId) } returns task

            // Act
            val result = taskController.getTaskById(taskId)

            // Assert
            result.statusCode shouldBe HttpStatus.OK
            result.body shouldNotBe null
            result.body?.id shouldBe taskId
            result.body?.title shouldBe "Review Document"
        }

        test("should return 404 when task not found") {
            // Arrange
            val taskId = 999L
            every { mockTaskService.getTaskById(taskId) } returns null

            // Act
            val result = taskController.getTaskById(taskId)

            // Assert
            result.statusCode shouldBe HttpStatus.NOT_FOUND
            result.body shouldBe null
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
                status = TaskStatus.COMPLETED,
                assignee = assignee,
                formId = 1
            )

            val page: Page<Task> = PageImpl(listOf(task), PageRequest.of(0, 10), 1)
            every { mockTaskService.searchTasks(assignee, null, any()) } returns page

            // Act
            val result = taskController.searchTasks(assignee = assignee, status = null, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].assignee shouldBe assignee
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
            every { mockTaskService.searchTasks(null, status, any()) } returns page

            // Act
            val result = taskController.searchTasks(assignee = null, status = status, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].status shouldBe status
        }

        test("should search tasks by assignee and status") {
            // Arrange
            val assignee = "alice@example.com"
            val status = TaskStatus.PENDING
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
            every { mockTaskService.searchTasks(assignee, status, any()) } returns page

            // Act
            val result = taskController.searchTasks(assignee = assignee, status = status, PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 1
            result.content[0].assignee shouldBe assignee
            result.content[0].status shouldBe status
        }
    }

    context("completeTask") {
        test("should complete task successfully") {
            // Arrange
            val taskId = 1L
            val assignee = "alice@example.com"
            val variables = mapOf(
                "approved" to true,
                "comments" to "Looks good"
            )

            val body = mapOf(
                "assignee" to assignee,
                "variables" to variables as Any
            )

            every { mockTaskService.completeTask(taskId, assignee, any()) } just runs

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe "Task completed successfully"
            verify { mockTaskService.completeTask(taskId, assignee, any()) }
        }

        test("should return 400 when assignee is missing") {
            // Arrange
            val taskId = 1L
            val body = mapOf(
                "variables" to mapOf("approved" to true) as Any
            )

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body?.contains("Missing assignee") shouldBe true
        }

        test("should handle missing variables") {
            // Arrange
            val taskId = 1L
            val assignee = "alice@example.com"
            val body = mapOf(
                "assignee" to assignee
            )

            every { mockTaskService.completeTask(taskId, assignee, any()) } just runs

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe "Task completed successfully"
        }

        test("should return 400 when service throws IllegalArgumentException") {
            // Arrange
            val taskId = 1L
            val assignee = "alice@example.com"
            val body = mapOf(
                "assignee" to assignee,
                "variables" to mapOf("approved" to true) as Any
            )

            every { mockTaskService.completeTask(taskId, assignee, any()) } throws IllegalArgumentException("Task not found")

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body?.contains("Task not found") shouldBe true
        }

        test("should return 409 when service throws IllegalStateException") {
            // Arrange
            val taskId = 1L
            val assignee = "alice@example.com"
            val body = mapOf(
                "assignee" to assignee,
                "variables" to mapOf("approved" to true) as Any
            )

            every { mockTaskService.completeTask(taskId, assignee, any()) } throws IllegalStateException("Task already completed")

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode.value() shouldBe 409
            result.body?.contains("Task already completed") shouldBe true
        }

        test("should serialize complex variable values") {
            // Arrange
            val taskId = 1L
            val assignee = "alice@example.com"
            val complexValue = mapOf(
                "nestedField" to "value",
                "number" to 42
            )
            val variables = mapOf(
                "complexData" to complexValue as Any
            )

            val body = mapOf(
                "assignee" to assignee,
                "variables" to variables as Any
            )

            every { mockTaskService.completeTask(taskId, assignee, any()) } just runs

            // Act
            val result = taskController.completeTask(taskId, body)

            // Assert
            result.statusCode shouldBe HttpStatus.OK
            verify { mockTaskService.completeTask(taskId, assignee, any()) }
        }
    }
})
