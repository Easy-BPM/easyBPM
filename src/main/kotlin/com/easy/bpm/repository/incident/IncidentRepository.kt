package com.easy.bpm.repository.incident

import com.easy.bpm.model.incident.Incident
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.incident.IncidentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface IncidentRepository : JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    fun findByProcessInstanceIdOrderByCreatedAtDesc(processInstanceId: Long): List<Incident>
    fun findByProcessInstanceId(processInstanceId: Long): List<Incident>

    fun findByStatus(status: IncidentStatus, pageable: Pageable): Page<Incident>

    fun findBySource(source: IncidentSource, pageable: Pageable): Page<Incident>

    fun findTopByProcessInstanceIdAndNodeIdAndSourceAndStatusInOrderByCreatedAtDesc(
        processInstanceId: Long,
        nodeId: String?,
        source: IncidentSource,
        statuses: Collection<IncidentStatus>
    ): Incident?

    fun countByStatus(status: IncidentStatus): Long

    fun countBySeverityAndStatusNot(severity: com.easy.bpm.model.incident.IncidentSeverity, status: IncidentStatus): Long

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.createdAt >= :since")
    fun countCreatedSince(@Param("since") since: LocalDateTime): Long

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int

    fun countByProcessInstanceId(processInstanceId: Long): Long

    @Query("SELECT i.id FROM Incident i WHERE i.processInstanceId = :processInstanceId")
    fun findIdsByProcessInstanceId(@Param("processInstanceId") processInstanceId: Long): List<Long>
}
