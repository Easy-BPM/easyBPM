package com.easy.bpm.decision

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class DecisionExpressions(mapper: ObjectMapper) {
    private val mapper = mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    private val path = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    fun isPath(expression: String) = path.matches(expression)
    fun readPath(values: Map<String, Any?>, expression: String): Any? {
        require(isPath(expression)) { "Invalid input path" }
        var value: Any? = values
        expression.split('.').forEach { key ->
            val obj = value as? Map<*, *> ?: throw DecisionException("MISSING_INPUT", "Required input path is missing")
            if (!obj.containsKey(key)) throw DecisionException("MISSING_INPUT", "Required input path is missing")
            value = obj[key]
        }
        return value
    }
    fun checkType(value: Any?, type: String) {
        val valid = when (type) {
            "string" -> value is String
            "number" -> value is Number && value.toString().toBigDecimalOrNull() != null
            "boolean" -> value is Boolean
            "date" -> value is String && Regex("\\d{4}-\\d{2}-\\d{2}").matches(value) && runCatching { LocalDate.parse(value) }.isSuccess
            "json" -> runCatching { mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(value) }.isSuccess
            else -> false
        }
        if (!valid) throw DecisionException("TYPE_MISMATCH", "Value does not match declared $type type")
        checkDepth(value, 0)
    }
    private fun checkDepth(value: Any?, depth: Int) {
        if (depth > 16) throw DecisionException("LIMIT", "JSON nesting exceeds 16")
        when (value) {
            is Map<*, *> -> value.values.forEach { checkDepth(it, depth + 1) }
            is List<*> -> value.forEach { checkDepth(it, depth + 1) }
        }
    }
    fun literal(text: String, type: String): Any? {
        val t = text.trim()
        val value: Any? = if (type == "date" && t.startsWith("date(\"") && t.endsWith("\")")) t.substring(6, t.length - 2)
        else try {
            val parser = mapper.factory.createParser(t)
            parser.use {
                val v = mapper.readValue(it, Any::class.java)
                if (it.nextToken() != null) throw IllegalArgumentException()
                v
            }
        } catch (_: Exception) { throw DecisionException("INVALID_LITERAL", "Invalid typed literal") }
        checkType(value, type)
        return value
    }
    fun output(text: String, type: String, inputs: Map<String, Any?>): Any? =
        if (isPath(text.trim()) && text.trim() !in setOf("true", "false", "null")) readPath(inputs, text.trim()).also { checkType(it, type) } else literal(text, type)

    fun validateOutput(text: String, type: String) {
        limit(text)
        if (isPath(text.trim()) && text.trim() !in setOf("true", "false", "null")) return
        literal(text, type)
    }
    fun predicate(text: String, type: String): (Any?) -> Boolean = parse(text.trim(), type, 0)
    private fun parse(text: String, type: String, depth: Int): (Any?) -> Boolean {
        limit(text)
        if (depth > 16) throw DecisionException("LIMIT", "Expression nesting exceeds 16")
        if (text.isBlank()) throw DecisionException("INCOMPLETE_RULE", "Rule cells cannot be empty")
        if (text == "-") return { true }
        if (text.startsWith("not(") && text.endsWith(")")) {
            val nested = parse(text.substring(4, text.length - 1).trim(), type, depth + 1)
            return { !nested(it) }
        }
        val alternatives = split(text)
        if (alternatives.size > 1) {
            val predicates = alternatives.map { parse(it.trim(), type, depth + 1) }
            return { v -> predicates.any { it(v) } }
        }
        if (text.first() in "[(" && text.last() in "])" && ".." in text) {
            ordered(type)
            val endpoints = text.substring(1, text.length - 1).split("..")
            if (endpoints.size != 2) throw DecisionException("INVALID_EXPRESSION", "Invalid interval")
            val low = literal(endpoints[0], type); val high = literal(endpoints[1], type)
            if (compare(low, high, type) > 0) throw DecisionException("INVALID_EXPRESSION", "Interval endpoints are reversed")
            return { v -> (compare(v, low, type).let { if (text.first() == '[') it >= 0 else it > 0 }) && (compare(v, high, type).let { if (text.last() == ']') it <= 0 else it < 0 }) }
        }
        val comparison = Regex("^(<=|>=|!=|<|>|=)\\s*(.+)$").matchEntire(text)
        if (comparison != null) {
            val op = comparison.groupValues[1]; val expected = literal(comparison.groupValues[2], type)
            if (op !in listOf("=", "!=")) ordered(type)
            return { v -> when (op) { "=" -> equal(v, expected); "!=" -> !equal(v, expected); "<" -> compare(v, expected, type) < 0; "<=" -> compare(v, expected, type) <= 0; ">" -> compare(v, expected, type) > 0; else -> compare(v, expected, type) >= 0 } }
        }
        val expected = literal(text, type)
        return { v -> equal(v, expected) }
    }
    fun equal(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal()) == 0
        a is Map<*, *> && b is Map<*, *> -> a.keys == b.keys && a.keys.all { equal(a[it], b[it]) }
        a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { equal(a[it], b[it]) }
        else -> a == b
    }
    private fun compare(a: Any?, b: Any?, type: String): Int = when (type) {
        "number" -> BigDecimal(a.toString()).compareTo(BigDecimal(b.toString()))
        "date" -> LocalDate.parse(a.toString()).compareTo(LocalDate.parse(b.toString()))
        else -> throw DecisionException("INVALID_EXPRESSION", "Ordering requires number or date")
    }
    private fun ordered(type: String) { if (type !in setOf("number", "date")) throw DecisionException("INVALID_EXPRESSION", "Ordering requires number or date") }
    private fun limit(text: String) { if (text.length > 4096) throw DecisionException("LIMIT", "Cell exceeds 4096 characters") }
    private fun split(text: String): List<String> {
        val parts = mutableListOf<String>(); var quoted = false; var escaped = false; var nesting = 0; var start = 0
        text.forEachIndexed { i, c ->
            if (escaped) { escaped = false; return@forEachIndexed }
            if (quoted && c == '\\') { escaped = true; return@forEachIndexed }
            if (c == '"') quoted = !quoted
            if (!quoted) {
                if (c in "[({") nesting++
                if (c in "])}") nesting--
                if (nesting > 16 || nesting < 0) throw DecisionException("INVALID_EXPRESSION", "Invalid expression nesting")
                if (c == ',' && nesting == 0) { parts += text.substring(start, i); start = i + 1 }
            }
        }
        if (quoted || nesting != 0) throw DecisionException("INVALID_EXPRESSION", "Unbalanced expression")
        parts += text.substring(start)
        return parts
    }
}
