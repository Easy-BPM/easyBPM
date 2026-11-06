package com.easy.bpm.controller

import com.easy.bpm.controller.data.TaskSearchFilterDto
import com.easy.bpm.controller.data.TaskSearchRequestDto
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.service.task.SlaSummary
import com.easy.bpm.service.task.TaskService
import com.easy.bpm.service.task.TaskSlaQueryService
import com.easy.bpm.service.task.TaskVisibility
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class TaskSlaControllerTest {
    private val queries = mockk<TaskSlaQueryService>()
    private val controller = TaskSlaController(queries, mockk<TaskService>())
    private val request = TaskSearchRequestDto(listOf(TaskSearchFilterDto("processInstanceId", value = 42L)))
    private val summary = SlaSummary(1, 2, 3, 4, 5, Instant.EPOCH, "UTC")

    @Test
    fun `admin summary accepts unrestricted visibility and preserves filters`() {
        val admin = identity(setOf("ACCESS_BPM_ADMIN"))
        every { queries.summary(request.filters, null) } returns summary

        assertEquals(summary, controller.summary(request, admin))
        verify(exactly = 1) { queries.summary(request.filters, null) }
    }

    @Test
    fun `portal summary retains username and candidate group restrictions`() {
        val user = identity(setOf("ACCESS_PROCESS_PORTAL"))
        val visibility = TaskVisibility(user.username, user.groups)
        every { queries.summary(request.filters, visibility) } returns summary

        assertEquals(summary, controller.summary(request, user))
        verify(exactly = 1) { queries.summary(request.filters, visibility) }
    }

    @Test
    fun `absent principal returns unauthorized without querying task data`() {
        val error = assertThrows(ResponseStatusException::class.java) { controller.summary(request, null) }

        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
        verify { queries wasNot Called }
    }

    private fun identity(permissions: Set<String>) =
        AuthenticatedUser(null, "reviewer", "", true, setOf("REVIEWERS"), permissions)
}
