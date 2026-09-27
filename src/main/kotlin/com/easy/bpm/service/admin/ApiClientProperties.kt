package com.easy.bpm.service.admin

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "easybpm.api-clients")
class ApiClientProperties {
    var auditRetentionDays: Int = 90
    var finalizationStaleMinutes: Long = 60
}
