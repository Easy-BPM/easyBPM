package com.easy.bpm.model.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "data_retention_settings")
data class DataRetentionSettings(
    @Id
    val id: Long = 1,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "completed_process_retention_days", nullable = false)
    var completedProcessRetentionDays: Long = 90,

    @Column(name = "completed_task_retention_days", nullable = false)
    var completedTaskRetentionDays: Long = 90,

    @Column(name = "batch_size", nullable = false)
    var batchSize: Int = 500,

    @Column(nullable = false, length = 120)
    var cron: String = "0 0 3 * * *",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
