package com.easy.bpm.repository.security

import com.easy.bpm.model.security.ApiClient
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import java.time.LocalDateTime

interface ApiClientRepository : JpaRepository<ApiClient, UUID>, JpaSpecificationExecutor<ApiClient> {
    fun existsByNormalizedName(normalizedName: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from ApiClient c where c.selector = :selector")
    fun findBySelectorForAuthentication(@Param("selector") selector: String): ApiClient?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ApiClient c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): ApiClient?

    @Modifying
    @Query(
        value = """
            update api_client
               set last_used_at = case when last_used_at is null or last_used_at < :usedAt then :usedAt else last_used_at end,
                   last_used_ip = case when last_used_at is null or last_used_at < :usedAt then :remoteIp else last_used_ip end
             where id = :id
        """,
        nativeQuery = true
    )
    fun updateLastUsedMonotonically(
        @Param("id") id: UUID,
        @Param("usedAt") usedAt: LocalDateTime,
        @Param("remoteIp") remoteIp: String
    ): Int
}
