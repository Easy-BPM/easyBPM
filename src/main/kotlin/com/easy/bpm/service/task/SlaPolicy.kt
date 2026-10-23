package com.easy.bpm.service.task

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class SlaStatus { ON_TRACK, AT_RISK, OVERDUE, COMPLETED }

/** Shared deployment/runtime rules. Durations are elapsed time, never calendar arithmetic. */
object SlaPolicy {
    private val variable = Regex("^\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}$")
    fun reference(value: String): String? = variable.matchEntire(value)?.groupValues?.get(1)
    fun duration(value: String, allowZero: Boolean = false): Duration {
        require(Regex("^P(?:[0-9]+D)?(?:T(?:[0-9]+H)?(?:[0-9]+M)?(?:[0-9]+(?:\\.[0-9]{1,9})?S)?)?$").matches(value)) { "Use an ISO elapsed duration such as PT4H (months/years unsupported)" }
        val parsed = try { Duration.parse(value) } catch (_: Exception) { throw IllegalArgumentException("Invalid or overflowing duration") }
        require(!parsed.isNegative && (allowZero || !parsed.isZero)) { "Duration must be positive" }
        require(parsed.seconds <= 9_223_372_036L) { "Duration exceeds supported limit" }
        return parsed
    }
    fun validate(policy: JsonNode) {
        require(policy.isObject && policy.toString().length <= 32768) { "SLA must be a bounded object" }
        val text = policy.path("duration")
        require(text.isTextual && text.asText().isNotBlank()) { "sla.duration is required" }
        val fixed = if (reference(text.asText()) == null) duration(text.asText()) else null
        policy.get("timeZone")?.let { require(it.isTextual && it.asText() in ZoneId.getAvailableZoneIds()) { "Invalid SLA IANA timezone" } }
        val ids = mutableSetOf<String>()
        for (kind in listOf("alerts", "escalations")) {
            val actions = policy.get(kind) ?: continue
            require(actions.isArray && actions.size() <= 100) { "sla.$kind must contain at most 100 actions" }
            actions.forEach { action ->
                val id = action.path("id")
                require(id.isTextual && id.asText().isNotBlank() && id.asText().length <= 100 && ids.add(id.asText())) { "Action IDs must be unique, nonblank and at most 100 characters" }
                require(!id.asText().startsWith("__sla_")) { "Action ID uses reserved prefix __sla_" }
                val offset = action.path(if (kind == "alerts") "beforeDue" else "afterDue")
                require(offset.isTextual) { "Action offset is required" }
                val parsed = duration(offset.asText(), kind == "escalations")
                if (kind == "alerts" && fixed != null) require(parsed < fixed) { "Alert must precede SLA deadline" }
                if (kind == "escalations") {
                    require(action.path("action").asText() in setOf("REASSIGN", "ADD_CANDIDATE_USER", "ADD_CANDIDATE_GROUP")) { "Unsupported escalation action" }
                    val target = action.path("target")
                    require(target.isTextual && target.asText().isNotBlank() && target.asText().length <= 255) { "Escalation target is required" }
                    require(!target.asText().contains("\${") || reference(target.asText()) != null) { "Target expression must be a single variable reference" }
                }
            }
        }
    }
    fun status(due: Instant?, risk: Instant?, completed: Boolean, now: Instant): SlaStatus? = when {
        due == null -> null
        completed -> SlaStatus.COMPLETED
        !now.isBefore(due) -> SlaStatus.OVERDUE
        risk != null && !now.isBefore(risk) -> SlaStatus.AT_RISK
        else -> SlaStatus.ON_TRACK
    }
}
