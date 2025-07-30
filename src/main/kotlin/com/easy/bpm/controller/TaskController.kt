package com.easy.bpm.controller

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.service.TaskService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService
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

    // 4. Completar tarefa
    @PostMapping("/{id}/complete")
    fun completeTask(
        @PathVariable id: Long,
        @RequestParam assignee: String
    ): ResponseEntity<String> {
        return try {
            taskService.completeTask(id, assignee)
            ResponseEntity.ok("Task completed successfully")
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ex.message)
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).body(ex.message)
        }
    }
}
