package com.easy.bpm.service.task

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.incident.*
import com.easy.bpm.model.process.ProcessInstanceEvent
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.UserGroupRepository
import com.easy.bpm.repository.incident.IncidentRepository
import com.easy.bpm.repository.incident.IncidentEventRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.sql.Timestamp
import java.util.UUID

@Service
class TaskSlaService(
    private val tasks: TaskRepository,
    private val instances: ProcessInstanceRepository,
    private val variables: ProcessVariableRepository,
    private val users: AppUserRepository,
    private val groups: UserGroupRepository,
    private val incidents: IncidentRepository,
    private val incidentEvents: IncidentEventRepository,
    private val timeline: ProcessInstanceEventRepository,
    private val jdbc: JdbcTemplate,
    transactionManager: org.springframework.transaction.PlatformTransactionManager,
    private val mapper: ObjectMapper,
    private val meters: MeterRegistry,
    @Value("\${easybpm.sla.time-zone:UTC}") val defaultTimeZone: String
) {
    private val transactions = TransactionTemplate(transactionManager)
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)
    init {
        ZoneId.of(defaultTimeZone)
        meters.gauge("task.sla.overdue", this) {
            jdbc.queryForObject("select count(*) from task where status='PENDING' and due_date <= current_timestamp", Long::class.java)?.toDouble() ?: 0.0
        }
    }

    /** Called in the task creation transaction, before exposing the newly created task. */
    fun configure(task: Task, node: JsonNode, now: Instant = Instant.now()) {
        val policy = node.path("config").get("sla") ?: return
        SlaPolicy.validate(policy)
        val duration = SlaPolicy.duration(resolve(policy.path("duration").asText(), task.processInstanceId))
        val due = try { now.plus(duration) } catch (_: Exception) { throw IllegalArgumentException("SLA deadline overflows supported timestamp") }
        require(due.atOffset(java.time.ZoneOffset.UTC).year <= 9999) { "SLA deadline exceeds supported year" }
        task.dueDate = due
        task.slaTimeZone = policy.path("timeZone").asText(defaultTimeZone)
        task.slaPolicy = policy.toString()
        var sequence = 0
        val actions = mutableListOf<Array<Any?>>()
        policy.path("alerts").forEach { action ->
            val offset = SlaPolicy.duration(action.path("beforeDue").asText())
            require(offset < duration) { "Alert must precede SLA deadline" }
            val scheduled = due.minus(offset)
            if (task.slaRiskAt == null || scheduled < task.slaRiskAt) task.slaRiskAt = scheduled
            actions += arrayOf(task.id, action.path("id").asText(), "ALERT", null, Timestamp.from(scheduled), sequence++)
        }
        policy.path("escalations").forEach { action ->
            val scheduled = try { due.plus(SlaPolicy.duration(action.path("afterDue").asText(), true)) } catch (_: Exception) { throw IllegalArgumentException("Escalation time overflows") }
            require(scheduled.atOffset(java.time.ZoneOffset.UTC).year <= 9999) { "Escalation time exceeds supported year" }
            actions += arrayOf(task.id, action.path("id").asText(), action.path("action").asText(), action.path("target").asText(), Timestamp.from(scheduled), sequence++)
        }
        task.slaRiskAt?.let { actions += arrayOf(task.id, "__sla_risk", "STATUS_AT_RISK", null, Timestamp.from(it), -2) }
        actions += arrayOf(task.id, "__sla_due", "STATUS_OVERDUE", null, Timestamp.from(due), -1)
        tasks.saveAndFlush(task)
        actions.forEach { jdbc.update("insert into task_sla_action(task_id,action_id,action_type,target,scheduled_at,sequence_no,outcome,attempts) values (?,?,?,?,?,?,'PENDING',0)", *it) }
        event(task, null, "CONFIGURED", now, now, "SUCCESS", "SLA deadline configured")
    }

    private fun resolve(text: String, instanceId: Long): String {
        val name = SlaPolicy.reference(text) ?: return text
        val value = variables.findByProcessInstanceIdAndName(instanceId, name)?.value
        require(value != null && value.isTextual && value.asText().isNotBlank()) { "SLA variable must contain a nonblank string" }
        return value.asText()
    }

    @Scheduled(fixedDelayString = "\${easybpm.sla.poll-ms:10000}", initialDelayString = "\${easybpm.sla.initial-delay-ms:0}")
    fun poll() {
        // A bounded candidate scan is followed by authoritative locks. Concurrent replicas recheck outcomes.
        val ids = jdbc.queryForList("select a.task_id from task_sla_action a join task t on t.id=a.task_id join process_instance p on p.id=t.process_instance_id where a.outcome='PENDING' and a.scheduled_at <= current_timestamp and p.status <> 'FAILED' group by a.task_id order by min(a.scheduled_at),a.task_id limit 100", Long::class.java)
        ids.forEach { id ->
            try { transactions.executeWithoutResult { process(id) } }
            catch (ex: Exception) { logger.error("SLA transaction rolled back taskId={} reason={}", id, ex.javaClass.simpleName) }
        }
    }

    fun process(taskId: Long, now: Instant = Instant.now()) {
        val task = tasks.findByIdForUpdate(taskId).orElse(null) ?: return
        val instance = instances.findByIdForUpdate(task.processInstanceId) ?: return
        if (instance.status == ProcessStatus.FAILED) return
        val rows = jdbc.queryForList("select * from task_sla_action where task_id=? and outcome='PENDING' and scheduled_at<=? order by scheduled_at,sequence_no", task.id, Timestamp.from(now))
        rows.forEach { row ->
            val eligible = task.status == TaskStatus.PENDING && instance.status in setOf(ProcessStatus.ACTIVE, ProcessStatus.WAITING)
            val scheduled = (row["scheduled_at"] as Timestamp).toInstant()
            val action = row["action_type"].toString()
            if (!eligible || (action == "ALERT" && !now.isBefore(task.dueDate))) {
                jdbc.update("update task_sla_action set outcome='SKIPPED',executed_at=? where id=?", Timestamp.from(now), row["id"])
                event(task, row["action_id"].toString(), "SKIPPED", scheduled, now, "SKIPPED", "Action no longer eligible")
            } else execute(task, row, now)
        }

    }

    private fun execute(task: Task, row: Map<String, Any?>, now: Instant, actor: String = "system") : Boolean {
        val actionId = row["action_id"].toString()
        val kind = row["action_type"].toString()
        val scheduled = (row["scheduled_at"] as Timestamp).toInstant()
        jdbc.update("update task_sla_action set attempts=attempts+1 where id=?", row["id"])
        try {
            if (kind != "ALERT" && !kind.startsWith("STATUS_")) {
                val target = resolve(row["target"].toString(), task.processInstanceId).trim()
                require(target.isNotBlank()) { "SLA target is blank" }
                if (kind == "ADD_CANDIDATE_GROUP") require(groups.findByCode(target) != null) { "SLA group target does not exist" }
                else require(users.findByUsername(target)?.enabled == true) { "SLA user target is unknown or inactive" }
                when (kind) {
                    "REASSIGN" -> { task.assignee = target; task.assignmentVersion++ }
                    "ADD_CANDIDATE_USER" -> task.candidateUsers.add(target)
                    "ADD_CANDIDATE_GROUP" -> task.candidateGroups.add(target)
                    else -> throw IllegalArgumentException("Unsupported SLA action")
                }
                tasks.saveAndFlush(task)
            }
            jdbc.update("update task_sla_action set outcome='SUCCESS',executed_at=? where id=?", Timestamp.from(now), row["id"])
            event(task, actionId, if (kind.startsWith("STATUS_")) kind.removePrefix("STATUS_") else if (kind == "ALERT") "ALERT" else "ESCALATED", scheduled, now, "SUCCESS", if (kind == "ALERT") "SLA advance alert published" else "SLA $kind executed", actor, (row["incident_id"] as? Number)?.toLong())
            if (!kind.startsWith("STATUS_")) recordMetric(if (kind == "ALERT") "task.sla.alerts" else "task.sla.escalations", "success")
            return true
        } catch (ex: IllegalArgumentException) {
            val existing = (row["incident_id"] as? Number)?.toLong()?.let { incidents.findById(it).orElse(null) }
            val incident = existing ?: Incident(processInstanceId = task.processInstanceId, nodeId = task.nodeId, source = IncidentSource.SLA, message = ex.message ?: "Invalid SLA target", externalReferenceId = row["id"].toString())
            incident.message = ex.message ?: "Invalid SLA target"
            incident.occurrenceCount = if (existing == null) 1 else incident.occurrenceCount + 1
            incident.updatedAt = LocalDateTime.now()
            incidents.saveAndFlush(incident)
            incidentEvents.save(IncidentEvent(incidentId = incident.id, eventType = if (existing == null) IncidentEventType.CREATED else IncidentEventType.OCCURRED_AGAIN, message = incident.message, actor = actor))
            jdbc.update("update task_sla_action set outcome='FAILED',executed_at=?,incident_id=?,assignment_version_at_failure=? where id=?", Timestamp.from(now), incident.id, task.assignmentVersion, row["id"])
            event(task, actionId, "ESCALATION_FAILED", scheduled, now, "FAILED", incident.message, actor, incident.id)
            recordMetric("task.sla.escalations", "failure")
            return false
        }
    }

    fun retry(incidentId: Long, actor: String): Incident = transactions.execute {
        val incident = incidents.findById(incidentId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        val action = jdbc.queryForList("select * from task_sla_action where incident_id=?", incidentId).firstOrNull() ?: throw ResponseStatusException(HttpStatus.CONFLICT, "SLA action unavailable")
        val task = tasks.findByIdForUpdate((action["task_id"] as Number).toLong()).orElseThrow { ResponseStatusException(HttpStatus.CONFLICT) }
        val instance = instances.findByIdForUpdate(task.processInstanceId) ?: throw ResponseStatusException(HttpStatus.CONFLICT)
        val fresh = jdbc.queryForMap("select * from task_sla_action where id=?", action["id"])
        require(incident.source == IncidentSource.SLA)
        if (task.status != TaskStatus.PENDING || instance.status !in setOf(ProcessStatus.ACTIVE, ProcessStatus.WAITING) || fresh["outcome"] != "FAILED") throw ResponseStatusException(HttpStatus.CONFLICT, "SLA retry is stale")
        if (fresh["action_type"] == "REASSIGN" && (fresh["assignment_version_at_failure"] as? Number)?.toLong() != task.assignmentVersion) throw ResponseStatusException(HttpStatus.CONFLICT, "Task assignment changed after failed action")
        if (fresh["action_type"] == "REASSIGN" && jdbc.queryForObject("select count(*) from task_sla_action where task_id=? and action_type='REASSIGN' and outcome='SUCCESS' and (scheduled_at > ? or (scheduled_at=? and sequence_no>?))", Long::class.java, task.id, fresh["scheduled_at"], fresh["scheduled_at"], fresh["sequence_no"]) != 0L) throw ResponseStatusException(HttpStatus.CONFLICT, "A later reassignment already succeeded")
        incidentEvents.save(IncidentEvent(incidentId = incident.id, eventType = IncidentEventType.RETRY_REQUESTED, message = "SLA retry requested", actor = actor))
        if (execute(task, fresh, Instant.now(), actor)) {
            incident.status = IncidentStatus.RESOLVED
            incident.resolvedBy = actor
            incident.resolvedAt = LocalDateTime.now()
            incident.resolutionAction = IncidentResolutionAction.RETRIED_SUCCESSFULLY
            incidentEvents.save(IncidentEvent(incidentId = incident.id, eventType = IncidentEventType.RESOLVED, message = "SLA retry succeeded", actor = actor))
        }
        incidents.save(incident)
    }!!

    private fun event(task: Task, actionId: String?, type: String, scheduled: Instant, now: Instant, outcome: String, message: String, actor: String = "system", incidentId: Long? = null) {
        jdbc.update("insert into task_sla_event(id,task_id,process_instance_id,action_id,event_type,scheduled_at,occurred_at,due_date,outcome,message,actor,incident_id) values (?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), task.id, task.processInstanceId, actionId, type, Timestamp.from(scheduled), Timestamp.from(now), Timestamp.from(task.dueDate), outcome, message, actor, incidentId)
        timeline.save(ProcessInstanceEvent(processInstanceId = task.processInstanceId, nodeId = task.nodeId, eventType = ProcessInstanceEventType.SLA, message = message, actor = actor, details = "taskId=${task.id}; actionId=${actionId ?: ""}; outcome=$outcome; scheduledAt=$scheduled; occurredAt=$now"))
        afterCommit { logger.info("SLA event taskId={} instanceId={} actionId={} eventType={} outcome={} scheduledAt={} occurredAt={} incidentId={}", task.id, task.processInstanceId, actionId, type, outcome, scheduled, now, incidentId) }
    }
    private fun recordMetric(name: String, outcome: String) {
        afterCommit { meters.counter(name, "outcome", outcome).increment() }
    }
    private fun afterCommit(action: () -> Unit) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(object : org.springframework.transaction.support.TransactionSynchronization {
                override fun afterCommit() { action() }
            })
        else action()
    }
}
