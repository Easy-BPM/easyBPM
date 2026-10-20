package com.easy.bpm.service.form

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequiredFormInputValidatorTest {
    private val mapper = ObjectMapper()
    private fun validate(type: String, value: String) = RequiredFormInputValidator.validate(
        mapper.readTree("""{"properties":{"answer":{"type":"$type"}},"required":["answer"]}"""),
        mapper.readTree("""{"answer":$value}"""))

    @Test fun `required values accept zero false and mathematically integral numbers`() {
        validate("number", "0")
        validate("boolean", "false")
        validate("integer", "0.0")
        validate("string", "\"text\"")
        validate("object", "{}")
        validate("array", "[false]")
    }

    @Test fun `required types are not coerced`() {
        listOf("number" to "\"0\"", "boolean" to "\"false\"", "string" to "false",
            "integer" to "1.5", "object" to "[]", "array" to "{}").forEach { (type, value) ->
            assertThrows(IllegalArgumentException::class.java) { validate(type, value) }
        }
    }

    @Test fun `empty required arrays are rejected and readonly is not a validation bypass`() {
        assertThrows(IllegalArgumentException::class.java) { validate("array", "[]") }
        assertThrows(IllegalArgumentException::class.java) {
            RequiredFormInputValidator.validate(mapper.readTree("""{"properties":{"answer":{"readOnly":true}},"required":["answer"]}"""), mapper.readTree("{}"))
        }
    }
}
