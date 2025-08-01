package com.easy.bpm.controller

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.service.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService,
    private val objectMapper: ObjectMapper
) {

    // 1. Listar todas as tarefas com paginação
    @GetMapping
    fun getTasks(pageable: Pageable): Page<Task> {
        return taskService.getTasks(pageable)
    }

    // 2. Buscar tarefa por ID
    @GetMapping("/{id}")
    fun getTaskById(@PathVariable id: Long): ResponseEntity<Task> {
        return taskService.getTaskById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    // 3. Buscar tarefas por status e/ou assignee
    @GetMapping("/search")
    fun searchTasks(
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) status: TaskStatus?,
        pageable: Pageable
    ): Page<Task> {
        return taskService.searchTasks(assignee, status, pageable)
    }

    // 4. Complete Task
    @PostMapping("/{id}/complete")
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
