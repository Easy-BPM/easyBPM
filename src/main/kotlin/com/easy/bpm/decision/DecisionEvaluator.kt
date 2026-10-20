package com.easy.bpm.decision

import org.springframework.stereotype.Component

@Component
class DecisionEvaluator(private val xml: DmnXml, private val expressions: DecisionExpressions) {
    fun validate(dmnXml: String): DecisionValidation {
        val table = try { xml.parse(dmnXml) } catch (e: DecisionException) { return DecisionValidation(false, listOf(DecisionIssue("ERROR", e.code, e.message!!))) }
        val issues = mutableListOf<DecisionIssue>()
        fun error(code: String, message: String, rule: String? = null, column: String? = null) { issues += DecisionIssue("ERROR", code, message, rule, column) }
        if (!Regex("[A-Za-z_][A-Za-z0-9_.-]{0,127}").matches(table.key)) error("INVALID_KEY", "Key must be an identifier of at most 128 characters")
        if (table.name.isBlank() || table.name.length > 255) error("INVALID_NAME", "Name is required and limited to 255 characters")
        if (table.hitPolicy !in setOf("UNIQUE", "FIRST", "COLLECT")) error("UNSUPPORTED_POLICY", "Supported policies: UNIQUE, FIRST, COLLECT")
        if (table.inputs.isEmpty() || table.inputs.size > 32 || table.outputs.isEmpty() || table.outputs.size > 32 || table.rules.isEmpty() || table.rules.size > 1000) error("LIMIT", "Tables require 1–32 input/output columns and 1–1000 rules")
        val identifiers = table.inputs.map { it.id } + table.outputs.map { it.id } + table.rules.map { it.id }
        if (identifiers.any { !Regex("[A-Za-z_][A-Za-z0-9_.-]{0,199}").matches(it) } || identifiers.distinct().size != identifiers.size) error("INVALID_IDS", "Column and rule identifiers must be unique nonblank identifiers")
        if (table.outputs.any { !expressions.isPath(it.name) || '.' in it.name } || table.outputs.map { it.name }.distinct().size != table.outputs.size) error("INVALID_OUTPUTS", "Output names must be unique simple identifiers")
        table.inputs.forEach { if (!expressions.isPath(it.expression)) error("INVALID_EXPRESSION", "Input expression must be an identifier or dotted path", column = it.id) }
        table.rules.forEach { rule ->
            if (rule.inputEntries.size != table.inputs.size || rule.outputEntries.size != table.outputs.size) error("INCOMPLETE_RULE", "Rule must have a cell for every column", rule.id)
            rule.inputEntries.zip(table.inputs).forEach { (cell, column) -> try { expressions.predicate(cell, column.type) } catch (e: DecisionException) { error(e.code, e.message!!, rule.id, column.id) } }
            rule.outputEntries.zip(table.outputs).forEach { (cell, column) -> try { expressions.validateOutput(cell, column.type) } catch (e: DecisionException) { error(e.code, e.message!!, rule.id, column.id) } }
        }
        if (issues.none { it.severity == "ERROR" }) {
            // A complete pairwise solver for arbitrary disjunction/negation is intentionally not claimed.
            // Prove overlap with bounded typed witnesses; retain mandatory runtime UNIQUE enforcement.
            if (table.rules.size <= 100) {
                var checks = 0
                for (a in table.rules.indices) for (b in a + 1 until table.rules.size) {
                    if (++checks > 5000) break
                    val left = table.rules[a]; val right = table.rules[b]
                    if (table.inputs.indices.all { c -> overlap(left.inputEntries[c], right.inputEntries[c], table.inputs[c].type) }) {
                        issues += DecisionIssue(if (table.hitPolicy == "UNIQUE") "ERROR" else "WARNING", "RULE_OVERLAP", "Rules ${left.id} and ${right.id} overlap", right.id)
                    }
                }
            }
            issues += DecisionIssue("WARNING", "OVERLAP_ANALYSIS_LIMITED", "Overlap analysis proves sampled literal/range intersections; negation, complex disjunctions and tables over 100 rows require runtime UNIQUE enforcement")
        }
        return DecisionValidation(issues.none { it.severity == "ERROR" }, issues, table)
    }
    private fun overlap(a: String, b: String, type: String): Boolean {
        if (a.trim() == "-" && b.trim() == "-") return true
        val literals = mutableListOf<Any?>()
        if (type == "boolean") literals.addAll(listOf(true, false))
        if (type == "string") literals += "__dmn_overlap_witness__"
        if (type == "json") literals.add(null)
        val tokens = Regex("date\\(\"[^\"]+\"\\)|\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?|true|false|null").findAll("$a,$b").map { it.value }.toList()
        tokens.forEach { runCatching { literals.add(expressions.literal(it, type)) } }
        if (type == "json") { runCatching { literals.add(expressions.literal(a, type)) }; runCatching { literals.add(expressions.literal(b, type)) } }
        if (type == "number") {
            val values = literals.filterIsInstance<Number>().map { it.toString().toBigDecimal() }.sorted()
            values.forEach { literals.add(it + java.math.BigDecimal.ONE); literals.add(it - java.math.BigDecimal.ONE) }
            values.zipWithNext().forEach { (x, y) -> literals.add((x + y).divide(java.math.BigDecimal(2))) }
            literals.add(java.math.BigDecimal.ZERO)
        }
        if (type == "date") literals.toList().filterIsInstance<String>().forEach { value -> runCatching { val date = java.time.LocalDate.parse(value); literals.add(date.plusDays(1).toString()); literals.add(date.minusDays(1).toString()) } }
        val p = expressions.predicate(a, type); val q = expressions.predicate(b, type)
        return literals.any { runCatching { p(it) && q(it) }.getOrDefault(false) }
    }
    fun evaluate(table: DecisionTable, inputs: Map<String, Any?>, version: Int? = null): EvaluationResult {
        val started = System.nanoTime()
        val values = table.inputs.map { input -> expressions.readPath(inputs, input.expression).also { expressions.checkType(it, input.type) } }
        val explanations = table.rules.map { rule ->
            val conditions = table.inputs.indices.map { i -> ConditionExplanation(table.inputs[i].id, rule.inputEntries[i], expressions.predicate(rule.inputEntries[i], table.inputs[i].type)(values[i])) }
            RuleExplanation(rule.id, conditions.all { it.matched }, conditions)
        }
        val matched = explanations.filter { it.matched }.map { it.ruleId }
        if (table.hitPolicy == "UNIQUE" && matched.size > 1) throw DecisionException("UNIQUE_VIOLATION", "Multiple rules match a UNIQUE decision")
        val selected = if (table.hitPolicy == "FIRST") matched.take(1) else matched
        val rows = selected.map { id -> table.rules.first { it.id == id }.let { rule -> table.outputs.indices.associate { i -> table.outputs[i].name to expressions.output(rule.outputEntries[i], table.outputs[i].type, inputs) } } }
        return EvaluationResult(table.key, version, table.hitPolicy, selected, if (table.hitPolicy == "COLLECT") rows else rows.firstOrNull(), explanations, (System.nanoTime() - started) / 1_000_000)
    }
    fun requireValid(xmlText: String): DecisionTable {
        val validation = validate(xmlText)
        if (!validation.valid) throw DecisionException("INVALID_DECISION", validation.issues.first { it.severity == "ERROR" }.message)
        return validation.table!!
    }
}
