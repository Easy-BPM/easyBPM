package com.easy.bpm.controller

import com.easy.bpm.controller.data.TaskResponseDto
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.service.TaskService
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.security.AppPermissions
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks", description = "Task management and completion")
class TaskController(
    private val taskService: TaskService
) {

    @GetMapping
    @Operation(summary = "Get all tasks", description = "Retrieve all tasks with pagination")
    fun getTasks(
        pageable: Pageable,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): Page<TaskResponseDto> {
        return if (principal == null || principal.isBpmAdmin()) {
            taskService.getTaskResponses(pageable)
        } else {
            taskService.getVisibleTaskResponses(principal.username, principal.groups, pageable)
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a specific task by its ID")
    fun getTaskById(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): ResponseEntity<TaskResponseDto> {
        if (principal == null || principal.isBpmAdmin()) {
            return taskService.getTaskResponseById(id)
                ?.let { ResponseEntity.ok(it) }
                ?: ResponseEntity.notFound().build()
        }
        return try {
            taskService.getVisibleTaskResponseById(id, principal.username, principal.groups)
                ?.let { ResponseEntity.ok(it) }
                ?: ResponseEntity.notFound().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(403).build()
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search tasks", description = "Search tasks by assignee and/or status with pagination")
    fun searchTasks(
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) status: TaskStatus?,
        pageable: Pageable,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): Page<TaskResponseDto> {
        return if (principal == null || principal.isBpmAdmin()) {
            taskService.searchTaskResponses(assignee, status, pageable)
        } else {
            taskService.searchVisibleTaskResponses(principal.username, principal.groups, assignee, status, pageable)
        }
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim a task", description = "Claim a shared/group task for the current authenticated user")
    fun claimTask(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): ResponseEntity<TaskResponseDto> {
        if (principal == null) {
            return ResponseEntity.status(401).build()
        }
        return try {
            ResponseEntity.ok(taskService.claimTask(id, principal.username, principal.groups))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(403).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).build()
        }
    }

    @PostMapping("/{id}/unclaim")
    @Operation(summary = "Unclaim a task", description = "Remove the current assignee and return the task to the available pool")
    fun unclaimTask(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): ResponseEntity<TaskResponseDto> {
        if (principal == null) {
            return ResponseEntity.status(401).build()
        }
        return try {
            ResponseEntity.ok(taskService.unclaimTask(id, principal.username, principal.groups))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(403).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).build()
        }
    }

    @PostMapping("/{id}/draft")
    @Operation(summary = "Save a task draft", description = "Persist task variables without completing the task")
    fun saveDraft(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): ResponseEntity<TaskResponseDto> {
        if (principal == null) {
            return ResponseEntity.status(401).build()
        }
        return try {
            ResponseEntity.ok(taskService.saveTaskDraft(id, principal.username, principal.groups, extractVariables(body)))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(403).build()
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).build()
        }
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('ACCESS_BPM_ADMIN')")
    @Operation(summary = "Reassign a task", description = "Admin operation to update the existing task assignee")
    fun reassignTask(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any?>
    ): ResponseEntity<TaskResponseDto> {
        return try {
            ResponseEntity.ok(taskService.reassignTask(id, body["assignee"] as? String))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).build()
        }
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
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal principal: AuthenticatedUser? = null
    ): ResponseEntity<String> {
        val assignee = principal?.username ?: (body["assignee"] as? String)
            ?: return ResponseEntity.badRequest().body("Missing assignee")

        val vars = extractVariables(body)

        return try {
            if (principal == null) {
                taskService.completeTask(id, assignee, vars)
            } else {
                taskService.completeTask(id, assignee, principal.groups, vars)
            }
            ResponseEntity.ok("Task completed successfully")
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(403).body("Forbidden")
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ex.message)
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(409).body(ex.message)
        }
    }


}

private fun extractVariables(body: Map<String, Any?>): Map<String, Any?> {
    return (body["variables"] as? Map<*, *>)?.mapNotNull {
        val key = it.key as? String
        val value = it.value
        if (key != null) key to value else null
    }?.toMap() ?: emptyMap()
}

private fun AuthenticatedUser.isBpmAdmin(): Boolean =
    permissionCodes.contains(AppPermissions.ACCESS_BPM_ADMIN)
