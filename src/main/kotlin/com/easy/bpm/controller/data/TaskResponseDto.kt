package com.easy.bpm.controller.data

import com.easy.bpm.enum.TaskStatus
import java.time.LocalDateTime

data class TaskResponseDto(
    val id: Long,
    val title: String?,
    val name: String,
    val description: String?,
    val processInstanceId: Long,
    val nodeId: String,
    val assignee: String?,
    val status: TaskStatus,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val formDbId: Long?,
    val formId: String? = null,
    val variables: Map<String, Any?>
)

