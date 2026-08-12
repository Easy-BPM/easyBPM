package com.easy.bpm.repository.task

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    fun findByAssignee(assignee: String, pageable: Pageable): Page<Task>
    fun findByStatus(status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByAssigneeAndStatus(assignee: String, status: TaskStatus, pageable: Pageable): Page<Task>
    fun findByProcessInstanceId(processInstanceId: Long): List<Task>

    @Query("select t.id from Task t where t.processInstanceId = :processInstanceId")
    fun findIdsByProcessInstanceId(processInstanceId: Long): List<Long>

    @Query(
        """
        select t.id from Task t
        where t.status = :status
          and t.completedAt is not null
          and t.completedAt < :before
        order by t.completedAt asc, t.id asc
        """
    )
    fun findCompletedRetentionCandidateIds(
        @Param("status") status: TaskStatus,
        @Param("before") before: LocalDateTime,
        pageable: Pageable
    ): List<Long>

    @Query(
        """
        select t.id from Task t
        where t.status = :status
          and t.completedAt is not null
          and t.completedAt < :before
          and t.processInstanceId not in :excludedProcessInstanceIds
        order by t.completedAt asc, t.id asc
        """
    )
    fun findCompletedRetentionCandidateIdsExcludingInstances(
        @Param("status") status: TaskStatus,
        @Param("before") before: LocalDateTime,
        @Param("excludedProcessInstanceIds") excludedProcessInstanceIds: Collection<Long>,
        pageable: Pageable
    ): List<Long>

    fun countByProcessInstanceId(processInstanceId: Long): Long

    fun findByProcessInstanceIdAndNodeIdAndStatus(processInstanceId: Long, nodeId: String, status: TaskStatus): List<Task>

    @Modifying
    fun deleteByProcessInstanceId(processInstanceId: Long): Int

    @Modifying
    @Query("delete from Task t where t.id in :taskIds")
    fun deleteByIdIn(@Param("taskIds") taskIds: Collection<Long>): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    fun findByIdForUpdate(id: Long): Optional<Task>
}
