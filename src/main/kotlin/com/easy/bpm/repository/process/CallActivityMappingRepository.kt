package com.easy.bpm.repository.process

import com.easy.bpm.model.process.CallActivityMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for managing call activity variable mappings between parent and child process instances.
 */
@Repository
interface CallActivityMappingRepository : JpaRepository<CallActivityMapping, Long> {

    /**
     * Find all mappings for a given parent instance.
     * Used to retrieve all child subprocess relationships.
     */
    fun findByParentInstanceId(parentInstanceId: Long): List<CallActivityMapping>

    /**
     * Find mapping by parent and child instance IDs.
     * Used to retrieve variable mapping for a specific parent-child relationship.
     */
    fun findByParentInstanceIdAndChildInstanceId(parentInstanceId: Long, childInstanceId: Long): CallActivityMapping?

    /**
     * Find mapping by call activity node ID and parent instance.
     * Used to check if a specific call activity node is currently executing.
     */
    fun findByParentInstanceIdAndCallActivityNodeId(parentInstanceId: Long, callActivityNodeId: String): CallActivityMapping?

    /**
     * Find all child instances for a parent instance.
     * Used for breadcrumb navigation in Admin UI.
     */
    fun findByParentInstanceId(parentInstanceId: Long, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<CallActivityMapping>

    /**
     * Check if a given child instance has a parent.
     * Used for parent lookup in Admin UI.
     */
    @Query(value = "SELECT cam FROM CallActivityMapping cam WHERE cam.childInstanceId = ?1")
    fun findByChildInstanceId(childInstanceId: Long): CallActivityMapping?

    /**
     * Delete all mappings for a child instance (cascade cleanup).
     */
    fun deleteByChildInstanceId(childInstanceId: Long)

    fun deleteByParentInstanceId(parentInstanceId: Long)

    /**
     * Check if there are active mappings for a parent instance.
     * Used to determine if parent is waiting for child completion.
     */
    @Query("SELECT COUNT(cam) > 0 FROM CallActivityMapping cam WHERE cam.parentInstanceId = ?1")
    fun hasActiveChildren(parentInstanceId: Long): Boolean

    /**
     * Find all active call activity mappings (parent-child relationships).
     * Used for audit trails and debugging hierarchy state.
     */
    @Query("SELECT cam FROM CallActivityMapping cam ORDER BY cam.createdAt DESC")
    fun findAllActiveMappings(): List<CallActivityMapping>
}

