package com.easy.bpm.repository.process

import com.easy.bpm.model.process.AdHocDecisionAudit
import org.springframework.data.jpa.repository.JpaRepository

interface AdHocDecisionAuditRepository : JpaRepository<AdHocDecisionAudit, Long> {
    fun findByProcessInstanceIdAndAdHocNodeIdOrderByCreatedAtDesc(processInstanceId: Long, adHocNodeId: String): List<AdHocDecisionAudit>
}

