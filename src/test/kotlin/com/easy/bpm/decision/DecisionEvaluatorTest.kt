package com.easy.bpm.decision

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DecisionEvaluatorTest {
    private val expressions = DecisionExpressions(ObjectMapper())
    private val evaluator = DecisionEvaluator(DmnXml(), expressions)
    private fun table(policy: String = "UNIQUE", entries: List<String> = listOf("< 10", ">= 10")) = DecisionTable("sample", "Sample", policy,
        listOf(DecisionInput("i", "Value", "value", "number")), listOf(DecisionOutput("o", "result", "string")), entries.mapIndexed { index, cell -> DecisionRule("r$index", inputEntries = listOf(cell), outputEntries = listOf("\"result$index\"")) })

    @Test fun `hit policies are deterministic including no match`() {
        assertEquals(mapOf("result" to "result0"), evaluator.evaluate(table(), mapOf("value" to 5)).outputs)
        assertNull(evaluator.evaluate(table(entries = listOf("< 0")), mapOf("value" to 5)).outputs)
        assertEquals(emptyList<Any>(), evaluator.evaluate(table("COLLECT", listOf("< 0")), mapOf("value" to 5)).outputs)
        val collect = evaluator.evaluate(table("COLLECT", listOf("-", "> 0")), mapOf("value" to 5))
        assertEquals(listOf("r0", "r1"), collect.matchedRuleIds)
        assertEquals(listOf(mapOf("result" to "result0"), mapOf("result" to "result1")), collect.outputs)
        assertEquals(listOf("r0"), evaluator.evaluate(table("FIRST", listOf("-", "> 0")), mapOf("value" to 5)).matchedRuleIds)
        assertThrows(DecisionException::class.java) { evaluator.evaluate(table("UNIQUE", listOf("-", "> 0")), mapOf("value" to 5)) }
    }
    @Test fun `strict types calendar dates JSON null and nested paths`() {
        listOf("1", true, null).forEach { value -> assertThrows(DecisionException::class.java) { expressions.checkType(value, "number") } }
        assertThrows(DecisionException::class.java) { evaluator.evaluate(table(), emptyMap()) }
        expressions.checkType("2024-02-29", "date")
        assertThrows(DecisionException::class.java) { expressions.checkType("2025-02-29", "date") }
        assertThrows(DecisionException::class.java) { expressions.checkType("2024-2-9", "date") }
        expressions.checkType(null, "json")
        assertTrue(expressions.predicate("-", "json")(null))
        assertTrue(expressions.predicate("{\"a\":[1,true]}", "json")(mapOf("a" to listOf(1, true))))
        assertTrue(expressions.predicate("{\"a\":[1,true]}", "json")(mapOf("a" to listOf(java.math.BigDecimal("1.0"), true))))
        assertEquals(true, expressions.readPath(mapOf("order" to mapOf("ok" to true)), "order.ok"))
        assertThrows(DecisionException::class.java) { expressions.readPath(mapOf("order" to null), "order.ok") }
    }
    @Test fun `grammar covers equality comparisons disjunction ranges negation and date constructor`() {
        listOf("= 2", "!= 3", "< 3", "<= 2", "> 1", ">= 2", "1,2,3", "[1..2]", "(1..3)", "not(3)").forEach { assertTrue(expressions.predicate(it, "number")(2), it) }
        assertFalse(expressions.predicate("(1..2)", "number")(2))
        assertTrue(expressions.predicate(">= date(\"2024-02-29\")", "date")("2024-03-01"))
        assertEquals("2024-02-29", expressions.literal("date(\"2024-02-29\")", "date"))
        assertEquals("x", expressions.output("customer.name", "string", mapOf("customer" to mapOf("name" to "x"))))
        assertEquals(false, expressions.output("false", "boolean", emptyMap()))
        listOf("java.lang.Runtime", "Math.random()", "1 + 2", "", "[2..1]", "not(not(not(abc)))").forEach { assertThrows(DecisionException::class.java) { expressions.predicate(it, "number") } }
        assertThrows(DecisionException::class.java) { expressions.predicate("> \"a\"", "string") }
        assertThrows(DecisionException::class.java) { expressions.literal("1 2", "number") }
    }
    @Test fun `high precision decimals retain distinct values`() {
        val a = expressions.literal("0.1000000000000000001", "number")
        val b = expressions.literal("0.1000000000000000002", "number")
        assertNotEquals(a, b)
        assertTrue(expressions.predicate("< 0.1000000000000000002", "number")(a))
        assertFalse(expressions.predicate("0.1", "number")(a))
    }
    @Test fun `XML rejects external resources unsupported semantics and excessive resources`() {
        val example = java.io.File("docs/examples/expense-approval.dmn").readText()
        assertTrue(evaluator.validate(example).valid)
        assertFalse(evaluator.validate(example.replace("hitPolicy=\"UNIQUE\"", "hitPolicy=\"ANY\"")).valid)
        assertFalse(evaluator.validate(example.replace("<dmn:decisionTable", "<dmn:decisionTable aggregation=\"SUM\"")).valid)
        assertFalse(evaluator.validate(example.replace("<dmn:inputExpression", "<dmn:inputExpression expressionLanguage=\"javascript\"")).valid)
        assertFalse(evaluator.validate("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>$example").valid)
        assertFalse(evaluator.validate(" ".repeat(1_000_001)).valid)
        assertThrows(DecisionException::class.java) { expressions.predicate("x".repeat(4097), "string") }
        assertThrows(DecisionException::class.java) { expressions.predicate("not(".repeat(17) + "1" + ")".repeat(17), "number") }
        assertThrows(DecisionException::class.java) { expressions.checkType((1..17).fold< Int, Any?>(null) { v, _ -> listOf(v) }, "json") }
    }
    @Test fun `validation detects incomplete and overlapping rules`() {
        val example = java.io.File("docs/examples/expense-approval.dmn").readText()
        val overlap = example.replace("&gt;= 1000", "&lt; 1000").replace(">= 1000", "&lt; 1000")
        assertTrue(evaluator.validate(overlap).issues.any { it.code == "RULE_OVERLAP" })
        val incomplete = example.replace("&lt; 1000", "")
        assertFalse(evaluator.validate(incomplete).valid)
    }
}
