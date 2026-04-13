package com.easy.bpm.controller

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.service.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val taskService: TaskService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping
    @Operation(summary = "Get all tasks", description = "Retrieve all tasks with pagination")
    fun getTasks(pageable: Pageable): Page<Task> {
        return taskService.getTasks(pageable)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a specific task by its ID")
    fun getTaskById(@PathVariable id: Long): ResponseEntity<Task> {
        return taskService.getTaskById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/search")
    @Operation(summary = "Search tasks", description = "Search tasks by assignee and/or status with pagination")
    fun searchTasks(
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) status: TaskStatus?,
        pageable: Pageable
    ): Page<Task> {
        return taskService.searchTasks(assignee, status, pageable)
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a task", description = "Mark a task as completed and provide task variables")
    fun completeTask(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any> // Espera: { "assignee": "joao", "variables": { "aprovado": "true" } }
    ): ResponseEntity<String> {
        val assignee = body["assignee"] as? String
            ?: return ResponseEntity.badRequest().body("Missing assignee")

        val vars = (body["variables"] as? Map<*, *>)?.mapNotNull {
            val key = it.key as? String
            val value = it.value
            if (key != null && value != null) key to serializeVariableValue(value) else null
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

    private fun serializeVariableValue(value: Any): String {
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            value.toString() // fallback
        }
    }

}
