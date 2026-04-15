package com.easy.bpm.repository.task

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface TaskRepository : JpaRepository<Task, Long> {
    fun findByAssignee(assignee: String, pageable: Pageable): Page<Task>
    fun findByStatus(status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByAssigneeAndStatus(assignee: String, status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByProcessInstanceId(processInstanceId: Long): List<Task>
    fun deleteByProcessInstanceId(processInstanceId: Long)

}