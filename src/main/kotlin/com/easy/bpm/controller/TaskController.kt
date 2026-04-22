package com.easy.bpm.controller

import com.easy.bpm.controller.data.TaskResponseDto
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.service.TaskService
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks", description = "Task management and completion")
class TaskController(
    private val taskService: TaskService
) {

    @GetMapping
    @Operation(summary = "Get all tasks", description = "Retrieve all tasks with pagination")
    fun getTasks(pageable: Pageable): Page<TaskResponseDto> {
        return taskService.getTaskResponses(pageable)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a specific task by its ID")
    fun getTaskById(@PathVariable id: Long): ResponseEntity<TaskResponseDto> {
        return taskService.getTaskResponseById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/search")
    @Operation(summary = "Search tasks", description = "Search tasks by assignee and/or status with pagination")
    fun searchTasks(
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) status: TaskStatus?,
        pageable: Pageable
    ): Page<TaskResponseDto> {
        return taskService.searchTaskResponses(assignee, status, pageable)
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a task", description = "Mark a task as completed and provide task variables")
    fun completeTask(
        @PathVariable id: Long,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Task completion payload including assignee and process variables",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(type = "object"),
                    examples = [
                        ExampleObject(
                            name = "complete-user-task",
                            summary = "Complete task with decision data",
                            value = """
                            {
                              "assignee": "joao",
                              "variables": {
                                "approved": true,
                                "comment": "Looks good",
                                "reviewedAt": "2026-04-15T16:00:00Z"
                              }
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody body: Map<String, Any> // Espera: { "assignee": "joao", "variables": { "aprovado": "true" } }
    ): ResponseEntity<String> {
        val assignee = body["assignee"] as? String
            ?: return ResponseEntity.badRequest().body("Missing assignee")

        val vars = (body["variables"] as? Map<*, *>)?.mapNotNull {
            val key = it.key as? String
            val value = it.value
            if (key != null && value != null) key to value else null
        }?.toMap() ?: emptyMap()

        return try {
            taskService.completeTask(id, assignee, vars)
            ResponseEntity.ok("Task completed successfully")
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ex.message)
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).body(ex.message)
        }
    }


}

