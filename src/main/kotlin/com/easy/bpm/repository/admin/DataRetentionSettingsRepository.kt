package com.easy.bpm.repository.admin

import com.easy.bpm.model.admin.DataRetentionSettings
import org.springframework.data.jpa.repository.JpaRepository

interface DataRetentionSettingsRepository : JpaRepository<DataRetentionSettings, Long>
