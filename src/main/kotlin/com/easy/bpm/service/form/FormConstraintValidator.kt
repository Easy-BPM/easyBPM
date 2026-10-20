package com.easy.bpm.service.form

import com.fasterxml.jackson.databind.JsonNode

/** Supported top-level value constraints; required presence is handled separately (#65). */
internal object FormConstraintValidator {
    fun validate(schema: JsonNode, values: JsonNode) {
        schema.path("properties").fields().forEach { (name, rule) ->
            val value = values.get(name) ?: return@forEach
            // Optional empty controls retain the Portal's existing unset-value contract.
            if (value.isNull || (value.isTextual && value.asText().isEmpty())) return@forEach
            fun check(valid: Boolean, reason: String) = require(valid) { "Invalid form field '$name': $reason" }
            when (rule.path("type").asText()) {
                "number", "integer" -> {
                    check(value.isNumber, "must be a number.")
                    val number = value.decimalValue()
                    if (rule.path("type").asText() == "integer") check(number.stripTrailingZeros().scale() <= 0, "must be an integer.")
                    rule.get("minimum")?.let { check(number >= it.decimalValue(), "must be at least ${it.decimalValue()}.") }
                    rule.get("maximum")?.let { check(number <= it.decimalValue(), "must be at most ${it.decimalValue()}.") }
                    rule.get("multipleOf")?.let {
                        val multiple = it.decimalValue()
                        check(multiple.signum() > 0, "has an invalid multipleOf rule.")
                        check(number.remainder(multiple).signum() == 0, "must be a multiple of $multiple.")
                    }
                }
                "string", "date" -> {
                    check(value.isTextual, "must be text.")
                    val text = value.asText()
                    val length = text.codePointCount(0, text.length)
                    rule.get("minLength")?.let { check(length >= it.asInt(), "needs at least ${it.asInt()} characters.") }
                    rule.get("maxLength")?.let { check(length <= it.asInt(), "allows at most ${it.asInt()} characters.") }
                    rule.get("pattern")?.let {
                        val pattern = try { Regex(it.asText()) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid form field '$name': has an invalid pattern rule.") }
                        check(pattern.containsMatchIn(text), "must match the configured pattern.")
                    }
                }
                "boolean" -> check(value.isBoolean, "must be a boolean.")
                "object" -> check(value.isObject, "must be an object.")
                "array" -> check(value.isArray, "must be an array.")
            }
            rule.get("enum")?.let { options ->
                check(options.any { option -> option == value || (option.isNumber && value.isNumber && option.decimalValue().compareTo(value.decimalValue()) == 0) }, "must be an allowed option.")
            }
        }
    }
}
