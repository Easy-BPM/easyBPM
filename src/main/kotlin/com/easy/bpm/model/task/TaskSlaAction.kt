package com.easy.bpm.model.task

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/** Durable schedules and alert publication ledger; scheduler accesses these rows under task locks. */
@Entity
@Table(name = "task_sla_action", uniqueConstraints = [UniqueConstraint(columnNames = ["task_id", "action_id"])])
class TaskSlaAction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(nullable = false) var taskId: Long = 0,
    @Column(nullable = false, length = 100) var actionId: String = "",
    @Column(nullable = false, length = 50) var actionType: String = "",
    var target: String? = null,
    @Column(nullable = false) var scheduledAt: Instant = Instant.EPOCH,
    @Column(nullable = false) var sequenceNo: Int = 0,
    @Column(nullable = false, length = 30) var outcome: String = "PENDING",
    @Column(nullable = false) var attempts: Int = 0,
    var executedAt: Instant? = null,
    var incidentId: Long? = null,
    var assignmentVersionAtFailure: Long? = null
)

@Entity
@Table(name = "task_sla_event")
class TaskSlaEvent(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var taskId: Long = 0,
    @Column(nullable = false) var processInstanceId: Long = 0,
    @Column(length = 100) var actionId: String? = null,
    @Column(nullable = false, length = 60) var eventType: String = "",
    @Column(nullable = false) var scheduledAt: Instant = Instant.EPOCH,
    @Column(nullable = false) var occurredAt: Instant = Instant.EPOCH,
    @Column(nullable = false) var dueDate: Instant = Instant.EPOCH,
    @Column(nullable = false, length = 30) var outcome: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") var message: String = "",
    @Column(nullable = false) var actor: String = "system",
    var incidentId: Long? = null
)
