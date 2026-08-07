package com.easy.bpm.repository.incident

import com.easy.bpm.model.incident.IncidentEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface IncidentEventRepository : JpaRepository<IncidentEvent, Long> {
    fun findByIncidentIdOrderByCreatedAtDesc(incidentId: Long): List<IncidentEvent>
    fun countByIncidentId(incidentId: Long): Long

    @Modifying
    fun deleteByIncidentId(incidentId: Long): Int

    @Query("select count(ie) from IncidentEvent ie where ie.incidentId in :incidentIds")
    fun countByIncidentIdIn(incidentIds: Collection<Long>): Long

    @Modifying
    @Query("delete from IncidentEvent ie where ie.incidentId in :incidentIds")
    fun deleteByIncidentIdIn(incidentIds: Collection<Long>): Int
}
