package com.easy.bpm.service.form

import com.fasterxml.jackson.databind.JsonNode

/** Task completion rules: required values must be present, non-null and usable. */
internal object RequiredFormInputValidator {
    fun validate(schema: JsonNode, values: JsonNode) {
        schema.path("required").forEach { required ->
            val name = required.asText()
            val value = values.path(name)
            val missing = value.isMissingNode || value.isNull ||
                (value.isTextual && value.asText().isBlank()) ||
                (value.isArray && value.isEmpty)
            require(!missing) { "Required form field '$name' must be provided." }
            val type = schema.path("properties").path(name).path("type").asText()
            val validType = when (type) {
                "string" -> value.isTextual
                "number" -> value.isNumber
                "integer" -> value.isNumber && value.decimalValue().stripTrailingZeros().scale() <= 0
                "boolean" -> value.isBoolean
                "object" -> value.isObject
                "array" -> value.isArray
                else -> true
            }
            require(validType) { "Required form field '$name' must have type '$type'." }
        }
    }
}
