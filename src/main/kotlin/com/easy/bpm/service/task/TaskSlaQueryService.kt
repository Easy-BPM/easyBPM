package com.easy.bpm.service.task

import com.easy.bpm.controller.data.TaskSearchFilterDto
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.task.TaskRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.sql.Timestamp
import java.util.UUID

data class SlaSummary(val onTrack: Long, val atRisk: Long, val overdue: Long, val completed: Long, val withoutSla: Long, val asOf: Instant, val timeZone: String)
data class SlaEventDto(val id: UUID, val taskId: Long, val processInstanceId: Long, val actionId: String?, val eventType: String, val scheduledAt: Instant, val occurredAt: Instant, val dueDate: Instant, val outcome: String, val message: String, val incidentId: Long?)
data class SlaMetricRow(val key: String, val label: String, val onTrack: Long, val atRisk: Long, val overdue: Long, val completed: Long, val withoutSla: Long, val totalSlaCompleted: Long, val completedWithinSlaCount: Long, val completedWithinSlaRate: Double?, val successfulEscalations: Long, val failedEscalations: Long, val alertToCompletionSampleCount: Long, val alertToCompletionAverageSeconds: Double?)

@Service
class TaskSlaQueryService(private val tasks: TaskRepository, private val mapper: ObjectMapper, private val jdbc: JdbcTemplate, private val sla: TaskSlaService) {
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun summary(filters: List<TaskSearchFilterDto>, visibility: TaskVisibility?): SlaSummary {
        val now = Instant.now()
        val base = TaskSearchSpecification.build(filters, visibility, mapper, now)
        fun count(status: SlaStatus?) = tasks.count(base.and(Specification { root, _, cb ->
            val due = root.get<Instant>("dueDate")
            val risk = root.get<Instant>("slaRiskAt")
            val completed = cb.equal(root.get<TaskStatus>("status"), TaskStatus.COMPLETED)
            when(status) {
                null -> cb.isNull(due)
                SlaStatus.COMPLETED -> cb.and(cb.isNotNull(due), completed)
                SlaStatus.OVERDUE -> cb.and(cb.not(completed), cb.lessThanOrEqualTo(due, now))
                SlaStatus.AT_RISK -> cb.and(cb.not(completed), cb.greaterThan(due, now), cb.lessThanOrEqualTo(risk, now))
                SlaStatus.ON_TRACK -> cb.and(cb.not(completed), cb.greaterThan(due, now), cb.or(cb.isNull(risk), cb.greaterThan(risk, now)))
            }
        }))
        return SlaSummary(count(SlaStatus.ON_TRACK), count(SlaStatus.AT_RISK), count(SlaStatus.OVERDUE), count(SlaStatus.COMPLETED), count(null), now, sla.defaultTimeZone)
    }

    fun events(taskId: Long, pageable: Pageable): Page<SlaEventDto> {
        val page = bounded(pageable)
        val rows = jdbc.query("select * from task_sla_event where task_id=? order by occurred_at desc,id desc limit ? offset ?", { rs, _ ->
            SlaEventDto(rs.getObject("id", UUID::class.java), rs.getLong("task_id"), rs.getLong("process_instance_id"), rs.getString("action_id"), rs.getString("event_type"), rs.getTimestamp("scheduled_at").toInstant(), rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("due_date").toInstant(), rs.getString("outcome"), rs.getString("message"), rs.getObject("incident_id")?.let { (it as Number).toLong() })
        }, taskId, page.pageSize, page.offset)
        return PageImpl(rows, page, jdbc.queryForObject("select count(*) from task_sla_event where task_id=?", Long::class.java, taskId) ?: 0)
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun metrics(groupBy: String, from: String?, to: String?, pageable: Pageable): Page<SlaMetricRow> {
        val dimension = when(groupBy.uppercase()) {
            "PROCESS" -> "d.process_id"
            "TASK" -> "d.process_id || ':' || t.node_id"
            "USER" -> "coalesce(t.assignee,'UNASSIGNED')"
            "GROUP" -> "coalesce(g.group_code,'NO_GROUP')"
            else -> throw IllegalArgumentException("groupBy must be PROCESS, TASK, USER or GROUP")
        }
        fun parse(value: String?) = value?.let { try { OffsetDateTime.parse(it).toInstant() } catch (_: Exception) { throw IllegalArgumentException("Due ranges require offset-qualified timestamps") } }
        val start = parse(from); val end = parse(to)
        require(start == null || end == null || start <= end) { "dueDateFrom must precede dueDateTo" }
        val now = Instant.now()
        val args = mutableListOf<Any>(Timestamp.from(now), Timestamp.from(now))
        var range = ""
        if (start != null) { range += " and t.due_date>=?"; args += Timestamp.from(start) }
        if (end != null) { range += " and t.due_date<=?"; args += Timestamp.from(end) }
        val groupJoin = if (groupBy.equals("GROUP", true)) "left join task_candidate_group g on g.task_id=t.id" else ""
        val cte = """
            with data as (
              select $dimension dimension,t.id,t.due_date,t.status,t.completed_within_sla,
              case when t.due_date is null then null when t.status='COMPLETED' then 'COMPLETED'
                when t.due_date<=? then 'OVERDUE' when t.sla_risk_at<=? then 'AT_RISK' else 'ON_TRACK' end sla_status,
              (select count(*) from task_sla_action a where a.task_id=t.id and a.action_type in ('REASSIGN','ADD_CANDIDATE_USER','ADD_CANDIDATE_GROUP') and a.outcome='SUCCESS') successes,
              (select count(*) from task_sla_event e where e.task_id=t.id and e.event_type='ESCALATION_FAILED') failures,
              case when t.status='COMPLETED' then extract(epoch from (t.sla_completed_at-(select min(e.occurred_at) from task_sla_event e where e.task_id=t.id and e.event_type='ALERT' and e.outcome='SUCCESS'))) else null end alert_seconds
              from task t join process_instance p on p.id=t.process_instance_id join process_definition d on d.id=p.process_definition_id $groupJoin
              where true $range
            ), grouped as (
              select dimension,count(*) filter(where sla_status='ON_TRACK') on_track,count(*) filter(where sla_status='AT_RISK') at_risk,
              count(*) filter(where sla_status='OVERDUE') overdue,count(*) filter(where sla_status='COMPLETED') completed,
              count(*) filter(where due_date is null) without_sla,
              count(*) filter(where due_date is not null and status='COMPLETED') total_completed,
              count(*) filter(where completed_within_sla=true) within_sla,
              coalesce(sum(successes),0) successes,coalesce(sum(failures),0) failures,count(alert_seconds) samples,avg(alert_seconds) average_seconds
              from data group by dimension
            )
        """.trimIndent()
        val page = bounded(pageable)
        val total = jdbc.queryForObject("$cte select count(*) from grouped", Long::class.java, *args.toTypedArray()) ?: 0L
        val rows = jdbc.query("$cte select * from grouped order by dimension limit ? offset ?", { rs, _ ->
            val completed = rs.getLong("total_completed"); val within = rs.getLong("within_sla")
            SlaMetricRow(rs.getString("dimension"), rs.getString("dimension"), rs.getLong("on_track"), rs.getLong("at_risk"), rs.getLong("overdue"), rs.getLong("completed"), rs.getLong("without_sla"), completed, within, if (completed == 0L) null else within.toDouble()/completed, rs.getLong("successes"), rs.getLong("failures"), rs.getLong("samples"), rs.getObject("average_seconds")?.let { (it as Number).toDouble() })
        }, *(args + page.pageSize + page.offset).toTypedArray())
        return PageImpl(rows, page, total)
    }

    private fun bounded(page: Pageable) = PageRequest.of(if(page.isPaged) page.pageNumber else 0, if(page.isPaged) page.pageSize.coerceAtMost(100) else 20)
}
