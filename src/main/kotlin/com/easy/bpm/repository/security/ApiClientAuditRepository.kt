package com.easy.bpm.repository.security

import com.easy.bpm.model.security.ApiClientAudit
import com.easy.bpm.model.security.ApiClientAuditCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

interface ApiClientAuditRepository : JpaRepository<ApiClientAudit, Long> {
    fun findAllByApiClientId(apiClientId: UUID, pageable: Pageable): Page<ApiClientAudit>
    fun findAllByApiClientIdAndCategory(apiClientId: UUID, category: ApiClientAuditCategory, pageable: Pageable): Page<ApiClientAudit>
    @Query("select a.id as id from ApiClientAudit a where a.createdAt < :cutoff order by a.createdAt")
    fun findIdsCreatedBefore(@Param("cutoff") cutoff: LocalDateTime, pageable: Pageable): Page<ApiClientAuditIdOnly>

    @Modifying(clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update ApiClientAudit a set a.httpStatus = :status, a.durationMs = :durationMs, a.outcome = :outcome where a.id = :id and a.outcome = 'STARTED'")
    fun finalizeStarted(
        @Param("id") id: Long,
        @Param("status") status: Int,
        @Param("durationMs") durationMs: Long,
        @Param("outcome") outcome: String
    ): Int

    @Modifying(clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update ApiClientAudit a set a.httpStatus = 500, a.durationMs = coalesce(a.durationMs, 0), a.outcome = 'FINALIZATION_FAILED' where a.category = :category and a.outcome = 'STARTED' and a.createdAt < :cutoff")
    fun finalizeStaleStarted(
        @Param("cutoff") cutoff: LocalDateTime,
        @Param("category") category: ApiClientAuditCategory
    ): Int
}

interface ApiClientAuditIdOnly { val id: Long }
