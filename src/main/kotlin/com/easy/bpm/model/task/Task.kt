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
    val processInstanceId: Long,

    val title: String? = null,

    val nodeId: String,
    var assignee: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_candidate_user", joinColumns = [JoinColumn(name = "task_id")])
    @Column(name = "username", nullable = false)
    var candidateUsers: MutableSet<String> = mutableSetOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_candidate_group", joinColumns = [JoinColumn(name = "task_id")])
    @Column(name = "group_code", nullable = false)
    var candidateGroups: MutableSet<String> = mutableSetOf(),

    @Enumerated(EnumType.STRING)
    var status: TaskStatus = TaskStatus.PENDING,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,

    val formId: Long? = null
)

