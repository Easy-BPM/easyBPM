package com.easy.bpm.controller

import com.easy.bpm.controller.data.TaskSearchRequestDto
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.service.task.*
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@RestController
class TaskSlaController(private val queries: TaskSlaQueryService, private val tasks: TaskService) {
    @PostMapping("/tasks/sla/summary")
    @PreAuthorize("hasAnyAuthority('ACCESS_BPM_ADMIN','ACCESS_PROCESS_PORTAL','SCOPE_tasks:read')")
    fun summary(@RequestBody request: TaskSearchRequestDto, @AuthenticationPrincipal principal: AuthenticatedUser?): SlaSummary {
        val user = principal ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val visibility = if (user.permissionCodes.contains("ACCESS_BPM_ADMIN")) null else TaskVisibility(user.username, user.groups)
        return queries.summary(request.filters, visibility)
    }

    @GetMapping("/tasks/{id}/sla/events")
    fun events(@PathVariable id: Long, pageable: Pageable, @AuthenticationPrincipal principal: AuthenticatedUser?): org.springframework.data.domain.Page<SlaEventDto> {
        val user = principal ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val task = if (user.permissionCodes.contains("ACCESS_BPM_ADMIN")) tasks.getTaskResponseById(id) else tasks.getVisibleTaskResponseById(id, user.username, user.groups)
        if (task == null) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return queries.events(id, pageable)
    }

    @GetMapping("/admin/sla/metrics")
    @PreAuthorize("hasAuthority('ACCESS_BPM_ADMIN')")
    fun metrics(@RequestParam(defaultValue="PROCESS") groupBy: String, @RequestParam(required=false) dueDateFrom: String?, @RequestParam(required=false) dueDateTo: String?, pageable: Pageable) = queries.metrics(groupBy, dueDateFrom, dueDateTo, pageable)
}
