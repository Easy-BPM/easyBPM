package com.easy.bpm.service

import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for managing variable input/output mappings between process instances.
 * 
 * Responsibilities:
 * 1. Apply input mappings (source → target variables)
 * 2. Apply output mappings (target → source variables)
 * 3. Handle propagate-all-variables mode
 * 4. Support nested property access (e.g., "order.customerId")
 * 5. Preserve variable types (JsonNode types, not stringification)
 * 6. Handle missing variables gracefully
 * 7. Track mapping statistics for monitoring
 */
@Service
class VariableMappingService(
    private val processVariableRepository: ProcessVariableRepository,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(VariableMappingService::class.java)
    }

    /**
     * Apply input variable mappings from source to target process instance.
     * Supports both explicit mappings and propagate-all mode.
     *
     * @param sourceInstanceId Source process instance ID
     * @param targetInstanceId Target process instance ID
     * @param mappings Map of source variable names to target variable names
     * @param propagateAll If true, copy all variables from source to target
     * @return Statistics about the mapping operation (variables mapped, skipped, etc.)
     */
    @Transactional
    fun applyInputMappings(
        sourceInstanceId: Long,
        targetInstanceId: Long,
        mappings: Map<String, String>,
        propagateAll: Boolean = false
    ): MappingStatistics {
        val startTime = System.currentTimeMillis()
        val stats = MappingStatistics()

        try {
            // Fetch all source variables
            val sourceVars = processVariableRepository.findByProcessInstanceId(sourceInstanceId)
                .associateBy { it.name }

            logger.debug(
                "Applying input mappings: source={}, target={}, propagateAll={}, sourceVarCount={}",
                sourceInstanceId, targetInstanceId, propagateAll, sourceVars.size
            )

            if (propagateAll) {
                // Copy all variables from source to target, preserving types
                sourceVars.forEach { (varName, sourceVar) ->
                    try {
                        processVariableRepository.save(
                            sourceVar.copy(
                                id = 0,
                                processInstanceId = targetInstanceId
                            )
                        )
                        stats.successCount++
                    } catch (ex: Exception) {
                        logger.warn("Failed to copy variable '{}': {}", varName, ex.message)
                        stats.failureCount++
                    }
                }
                stats.mode = "propagate_all"
                stats.variablesMapped = sourceVars.size
            } else {
                // Apply explicit mappings
                stats.mode = "explicit_mapping"
                mappings.forEach { (sourceName, targetName) ->
                    try {
                        val sourceVar = sourceVars[sourceName]
                        if (sourceVar != null) {
                            processVariableRepository.save(
                                sourceVar.copy(
                                    id = 0,
                                    name = targetName,
                                    processInstanceId = targetInstanceId
                                )
                            )
                            stats.successCount++
                            stats.variablesMapped++
                            logger.debug("Mapped variable '{}' -> '{}'", sourceName, targetName)
                        } else {
                            logger.warn("Source variable '{}' not found, skipping mapping to '{}'", sourceName, targetName)
                            stats.skippedCount++
                        }
                    } catch (ex: Exception) {
                        logger.error("Error mapping '{}' -> '{}': {}", sourceName, targetName, ex.message)
                        stats.failureCount++
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            stats.durationMs = duration
            logger.debug("Input mapping completed in {}ms: {}", duration, stats)
            return stats

        } catch (ex: Exception) {
            logger.error("Unexpected error during input mapping", ex)
            stats.failureCount++
            stats.durationMs = System.currentTimeMillis() - startTime
            throw ex
        }
    }

    /**
     * Apply output variable mappings from source to target process instance.
     * Typically used to propagate child results back to parent.
     *
     * @param sourceInstanceId Source process instance ID (typically child)
     * @param targetInstanceId Target process instance ID (typically parent)
     * @param mappings Map of source variable names to target variable names
     * @param propagateAll If true, copy all variables from source to target
     * @return Statistics about the mapping operation
     */
    @Transactional
    fun applyOutputMappings(
        sourceInstanceId: Long,
        targetInstanceId: Long,
        mappings: Map<String, String>,
        propagateAll: Boolean = false
    ): MappingStatistics {
        return applyInputMappings(sourceInstanceId, targetInstanceId, mappings, propagateAll)
    }

    /**
     * Map a single variable from source to target with type preservation.
     * Supports nested property access via dot notation.
     *
     * @param sourceVariable Source variable to map
     * @param targetVariableName Name to use in target instance
     * @param targetInstanceId Target process instance ID
     * @return true if mapping successful, false otherwise
     */
    @Transactional
    fun mapVariable(
        sourceVariable: ProcessVariable,
        targetVariableName: String,
        targetInstanceId: Long
    ): Boolean {
        return try {
            processVariableRepository.save(
                sourceVariable.copy(
                    id = 0,
                    name = targetVariableName,
                    processInstanceId = targetInstanceId
                )
            )
            true
        } catch (ex: Exception) {
            logger.error("Failed to map variable '{}' to '{}': {}", 
                sourceVariable.name, targetVariableName, ex.message)
            false
        }
    }

    /**
     * Extract a nested property value from a variable using dot notation.
     * Example: getNestedProperty(variable, "order.customerId") returns variable.order.customerId
     *
     * @param variable The variable containing the nested value
     * @param propertyPath Dot-separated path (e.g., "order.customerId")
     * @return The extracted value, or null if path not found
     */
    fun getNestedProperty(variable: ProcessVariable, propertyPath: String): JsonNode? {
        return try {
            val parts = propertyPath.split(".")
            var current: JsonNode = variable.value

            for (part in parts) {
                if (!current.isObject) {
                    logger.debug("Cannot access nested property '{}' on non-object type", propertyPath)
                    return null
                }
                current = current.get(part) ?: return null
            }

            current
        } catch (ex: Exception) {
            logger.debug("Error extracting nested property '{}': {}", propertyPath, ex.message)
            null
        }
    }

    /**
     * Set a nested property value in a variable using dot notation.
     * Example: setNestedProperty(variable, "order.customerId", value) sets variable.order.customerId = value
     *
     * @param variable The variable to modify
     * @param propertyPath Dot-separated path (e.g., "order.customerId")
     * @param value The value to set
     * @return Modified variable with nested property set
     */
    fun setNestedProperty(variable: ProcessVariable, propertyPath: String, value: JsonNode): ProcessVariable {
        return try {
            val parts = propertyPath.split(".")
            if (parts.isEmpty()) return variable

            val mutableValue = objectMapper.readValue(variable.value.toString(), ObjectNode::class.java)

            // Navigate/create path
            var current: ObjectNode = mutableValue
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                var node = current.get(part)
                if (node == null || !node.isObject) {
                    node = objectMapper.createObjectNode()
                    current.set<JsonNode>(part, node)
                }
                current = node as ObjectNode
            }

            // Set final property
            current.set<JsonNode>(parts.last(), value)

            variable.copy(value = objectMapper.readTree(mutableValue.toString()))
        } catch (ex: Exception) {
            logger.error("Error setting nested property '{}': {}", propertyPath, ex.message)
            variable
        }
    }

    /**
     * Merge variables from source into target, with target taking precedence.
     * Useful for combining process variables after mapping.
     *
     * @param source Map of source variables
     * @param target Map of target variables
     * @return Merged map with target overriding source
     */
    fun mergeVariables(
        source: Map<String, ProcessVariable>,
        target: Map<String, ProcessVariable>
    ): Map<String, ProcessVariable> {
        return source + target  // target overwrites source on key collision
    }

    /**
     * Filter variables by a predicate.
     * Useful for selective mapping.
     *
     * @param variables Variables to filter
     * @param predicate Filter condition
     * @return Filtered map
     */
    fun filterVariables(
        variables: Map<String, ProcessVariable>,
        predicate: (ProcessVariable) -> Boolean
    ): Map<String, ProcessVariable> {
        return variables.filterValues { predicate(it) }
    }

    /**
     * Statistics about a variable mapping operation.
     */
    data class MappingStatistics(
        var mode: String = "unknown",  // "explicit_mapping" or "propagate_all"
        var variablesMapped: Int = 0,
        var successCount: Int = 0,
        var skippedCount: Int = 0,
        var failureCount: Int = 0,
        var durationMs: Long = 0
    ) {
        fun isSuccess(): Boolean = failureCount == 0

        override fun toString(): String {
            return "MappingStatistics(mode=$mode, mapped=$variablesMapped, success=$successCount, skipped=$skippedCount, failed=$failureCount, duration=${durationMs}ms)"
        }
    }
}
