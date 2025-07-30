package com.easy.bpm.model.task

import com.easy.bpm.enum.TaskStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
data class Task(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "process_instance_id", nullable = false)
    val processInstanceId: Long,  // Agora apenas o ID

    val nodeId: String,
    var assignee: String? = null,

    @Enumerated(EnumType.STRING)
    var status: TaskStatus = TaskStatus.PENDING,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null
)
