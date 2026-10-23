package com.easy.bpm.service.process

import com.easy.bpm.decision.DecisionDefinition
import com.easy.bpm.decision.DecisionService
import com.easy.bpm.decision.EvaluationResult
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.service.process.handler.ProcessBusinessRuleTaskHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProcessBusinessRuleTaskHandlerTest {
    private val decisions = mockk<DecisionService>(relaxed = true)
    private val variables = mockk<ProcessVariableManager>(relaxed = true)
    private val mapper = ObjectMapper()
    private val handler = ProcessBusinessRuleTaskHandler(decisions, variables, mapper)
    private val instance = ProcessInstance(id = 42, processDefinition = ProcessDefinition(key = "p", processName = "p", definitionJson = "<xml/>", version = 1), status = ProcessStatus.ACTIVE)
    private val definition = DecisionDefinition(key = "eligibility", version = 2)
    private fun node(outputs: String = """[{"sourceName":"approved","value":"approved","target":"variable","type":"boolean"}]""") = mapper.readTree("""{"id":"rules","config":{"decisionKey":"eligibility","decisionVersion":2,"inputs":[{"targetName":"age","source":"variable","value":"age","type":"number"}],"outputs":$outputs}}""")
    private fun setup(outputs: Any?) {
        every { decisions.resolveForTask("eligibility", 2, 42, "rules") } returns definition
        every { variables.getProcessVariablesAsMap(42) } returns mapOf("age" to 21)
        every { decisions.evaluateDefinition(definition, mapOf("age" to 21), 42, "rules") } returns EvaluationResult("eligibility", 2, "UNIQUE", listOf("adult"), outputs, emptyList(), 1)
    }

    @Test fun `typed inputs and outputs preserve values`() {
        setup(mapOf("approved" to true))
        handler.execute(instance, node())
        verify(exactly = 1) { variables.assignProcessVariables(42, mapOf("approved" to true)) }
    }

    @Test fun `all mappings validated before any write and safe failure audit emitted`() {
        setup(mapOf("approved" to true))
        val error = assertThrows(IllegalArgumentException::class.java) {
            handler.execute(instance, node("""[{"sourceName":"approved","value":"approved","target":"variable","type":"boolean"},{"sourceName":"secretMissing","value":"other","target":"variable","type":"string"}]"""))
        }
        verify(exactly = 0) { variables.assignProcessVariables(any(), any()) }
        verify { decisions.recordFailure(definition, "eligibility", 42, "rules", "MISSING_DECISION_OUTPUT", any(), mapOf("age" to 21), any()) }
        assertFalse(error.message!!.contains("secretMissing"))
    }

    @Test fun `missing input fails before evaluation`() {
        setup(mapOf("approved" to true))
        every { variables.getProcessVariablesAsMap(42) } returns emptyMap()
        assertThrows(IllegalArgumentException::class.java) { handler.execute(instance, node()) }
        verify(exactly = 0) { decisions.evaluateDefinition(any(), any(), any(), any()) }
    }

    @Test fun `collect maps entire ordered result as JSON`() {
        val rows = listOf(mapOf("approved" to true), mapOf("approved" to false))
        setup(rows)
        handler.execute(instance, node("""[{"sourceName":"${'$'}result","value":"matches","target":"variable","type":"json"}]"""))
        verify { variables.assignProcessVariables(42, mapOf("matches" to rows)) }
    }

    @Test fun `incorrect mapped type is rejected without coercion`() {
        setup(mapOf("approved" to "true"))
        assertThrows(IllegalArgumentException::class.java) { handler.execute(instance, node()) }
        verify(exactly = 0) { variables.assignProcessVariables(any(), any()) }
    }

    @Test fun `collect named output maps ordered array and empty array`() {
        setup(listOf(mapOf("approved" to true), mapOf("approved" to false)))
        handler.execute(instance, node())
        verify { variables.assignProcessVariables(42, mapOf("approved" to listOf(true, false))) }
        setup(emptyList<Map<String, Any?>>())
        handler.execute(instance, node())
        verify { variables.assignProcessVariables(42, mapOf("approved" to emptyList<Boolean>())) }
    }

    @Test fun `dotted input path resolves nested object`() {
        setup(mapOf("approved" to true))
        every { variables.getProcessVariablesAsMap(42) } returns mapOf("customer" to mapOf("age" to 21))
        val nested = mapper.readTree(node().toString().replace("\"value\":\"age\"", "\"value\":\"customer.age\""))
        handler.execute(instance, nested)
        verify { decisions.evaluateDefinition(definition, mapOf("age" to 21), 42, "rules") }
    }

    @Test fun `nested outputs merge siblings without mutating snapshot`() {
        setup(mapOf("approved" to true))
        val original = mapOf("age" to 21, "customer" to mapOf("age" to 21, "approved" to false))
        every { variables.getProcessVariablesAsMap(42) } returns original
        val target = """[{"sourceName":"approved","value":"customer.approved","type":"boolean"},{"sourceName":"approved","value":"customer.flags.eligible","type":"boolean"}]"""
        handler.execute(instance, node(target))
        val expected = mapOf("customer" to mapOf("age" to 21, "approved" to true, "flags" to mapOf("eligible" to true)))
        verify { variables.assignProcessVariables(42, expected) }
        assertEquals(false, (original["customer"] as Map<*, *>)["approved"])
    }
    @Test fun `absent and null nested parents are created but later failure never mutates inputs`() {
        listOf(emptyMap<String, Any?>(), mapOf("customer" to null)).forEach { root ->
            setup(mapOf("approved" to true))
            every { variables.getProcessVariablesAsMap(42) } returns (root + ("age" to 21))
            handler.execute(instance, node("""[{"sourceName":"approved","value":"customer.flags.eligible","type":"boolean"}]"""))
        }
        verify(exactly = 2) { variables.assignProcessVariables(42, mapOf("customer" to mapOf("flags" to mapOf("eligible" to true)))) }
        clearMocks(variables, answers = false)
        setup(mapOf("approved" to true))
        val original = mapOf("age" to 21, "customer" to mapOf("approved" to false), "scalar" to 1)
        every { variables.getProcessVariablesAsMap(42) } returns original
        assertThrows(IllegalArgumentException::class.java) { handler.execute(instance, node("""[{"sourceName":"approved","value":"customer.approved","type":"boolean"},{"sourceName":"approved","value":"scalar.child","type":"boolean"}]""")) }
        verify(exactly = 0) { variables.assignProcessVariables(any(), any()) }
        assertEquals(false, (original["customer"] as Map<*, *>)["approved"])
    }
    @Test fun `overlapping targets and scalar parents reject every write`() {
        listOf(listOf("customer", "customer.approved"), listOf("customer.approved", "customer"), listOf("customer.approved", "customer.approved")).forEach { targets ->
            setup(mapOf("approved" to true))
            val mappings = targets.joinToString(",", "[", "]") { """{"sourceName":"approved","value":"$it","type":"boolean"}""" }
            assertThrows(IllegalArgumentException::class.java) { handler.execute(instance, node(mappings)) }
        }
        setup(mapOf("approved" to true))
        every { variables.getProcessVariablesAsMap(42) } returns mapOf("age" to 21, "customer" to false)
        assertThrows(IllegalArgumentException::class.java) { handler.execute(instance, node("""[{"sourceName":"approved","value":"customer.approved","type":"boolean"}]""")) }
        verify(exactly = 0) { variables.assignProcessVariables(any(), any()) }
    }
    @Test fun `output named result is mapped as ordinary output`() {
        setup(mapOf("result" to true))
        handler.execute(instance, node("""[{"sourceName":"result","value":"approved","target":"variable","type":"boolean"}]"""))
        verify { variables.assignProcessVariables(42, mapOf("approved" to true)) }
    }
}
