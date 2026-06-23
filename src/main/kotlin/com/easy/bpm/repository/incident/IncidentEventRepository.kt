package com.easy.bpm.repository.incident

import com.easy.bpm.model.incident.IncidentEvent
import org.springframework.data.jpa.repository.JpaRepository

interface IncidentEventRepository : JpaRepository<IncidentEvent, Long> {
    fun findByIncidentIdOrderByCreatedAtDesc(incidentId: Long): List<IncidentEvent>
    fun deleteByIncidentId(incidentId: Long)
}
