package com.easy.bpm.service.task

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SlaPolicyTest {
    private val mapper = ObjectMapper()
    @Test fun `elapsed days preserve 24 hours across DST and exact status boundaries`() {
        assertEquals(Duration.ofHours(24), SlaPolicy.duration("P1D"))
        val due = Instant.parse("2026-03-08T08:00:00Z")
        val risk = due.minusSeconds(1800)
        assertEquals(SlaStatus.ON_TRACK, SlaPolicy.status(due, risk, false, risk.minusNanos(1)))
        assertEquals(SlaStatus.AT_RISK, SlaPolicy.status(due, risk, false, risk))
        assertEquals(SlaStatus.OVERDUE, SlaPolicy.status(due, risk, false, due))
        assertEquals(SlaStatus.COMPLETED, SlaPolicy.status(due, risk, true, due))
        assertNull(SlaPolicy.status(null, null, false, due))
    }
    @Test fun `static errors reject units invalid shapes duplicate ids expressions and overflow`() {
        listOf("P1M","P1Y","PT0S","-PT1H","PT999999999999999999999S","PT1.1234567891S").forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { SlaPolicy.duration(invalid) } }
        val invalid = listOf(
            """{"duration":"PT1H","timeZone":"wrong/zone"}""",
            """{"duration":"PT1H","alerts":[{"id":"a","beforeDue":"PT1H"}]}""",
            """{"duration":"PT1H","alerts":[{"id":"a","beforeDue":"PT1M"},{"id":"a","beforeDue":"PT2M"}]}""",
            """{"duration":"PT1H","escalations":[{"id":"e","afterDue":"PT0S","action":"COMPLETE","target":"x"}]}""",
            """{"duration":"PT1H","alerts":{}}"""
        )
        invalid.forEach { json -> assertThrows(IllegalArgumentException::class.java) { SlaPolicy.validate(mapper.readTree(json)) } }
        SlaPolicy.validate(mapper.readTree("""{"duration":"${'$'}{deadline}","escalations":[{"id":"e","afterDue":"PT0S","action":"REASSIGN","target":"${'$'}{manager}"}]}"""))
        assertEquals("deadline", SlaPolicy.reference("${'$'}{deadline}"))
        assertNull(SlaPolicy.reference("prefix ${'$'}{deadline}"))
    }
}
