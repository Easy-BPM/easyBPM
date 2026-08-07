package com.easy.bpm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "easybpm.retention")
class DataRetentionProperties {
    var enabled: Boolean = false
    var completedProcessRetentionDays: Long = 90
    var completedTaskRetentionDays: Long = 90
    var batchSize: Int = 500
    var cron: String = "0 0 3 * * *"
}
