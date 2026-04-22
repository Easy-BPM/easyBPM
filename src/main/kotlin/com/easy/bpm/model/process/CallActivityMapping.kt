package com.easy.bpm.model.process

import jakarta.persistence.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.hibernate.annotations.Type
import java.time.LocalDateTime

/**
 * Represents a mapping between parent and child process instances for call activity (subprocess) execution.
 * Tracks variable input/output mappings and manages the parent-child relationship lifecycle.
 */
@Entity
@Table(
    name = "call_activity_mapping",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["parent_instance_id", "child_instance_id", "call_activity_node_id"],
            name = "unique_call_activity_mapping"
        )
    ],
    indexes = [
        Index(name = "idx_call_activity_mapping_parent_id", columnList = "parent_instance_id"),
        Index(name = "idx_call_activity_mapping_child_id", columnList = "child_instance_id"),
        Index(name = "idx_call_activity_mapping_call_activity_node", columnList = "call_activity_node_id")
    ]
)
data class CallActivityMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * Reference to the parent process instance.
     * When parent completes or is deleted, all its child mappings are cascaded deleted.
     */
    @Column(name = "parent_instance_id", nullable = false)
    val parentInstanceId: Long,

    /**
     * Reference to the child process instance.
     * When child completes, parent resumes from completion node.
     */
    @Column(name = "child_instance_id", nullable = false)
    val childInstanceId: Long,

    /**
     * ID of the call activity node that triggered this subprocess.
     * Used to resume parent process at the correct node.
     */
    @Column(name = "call_activity_node_id", nullable = false, length = 255)
    val callActivityNodeId: String,

    /**
     * Input variable mappings: parent variable name -> child variable name.
     * Example: {"orderId": "order_id", "customerId": "customer_id"}
     * Applied when child instance starts.
     */
    @Column(columnDefinition = "jsonb", nullable = false)
    val inputMappings: String = "{}",  // JSON string to avoid Jackson complexity in entity

    /**
     * Output variable mappings: child variable name -> parent variable name.
     * Example: {"status": "paymentStatus", "transactionId": "tx_id"}
     * Applied when child instance completes.
     */
    @Column(columnDefinition = "jsonb", nullable = false)
    val outputMappings: String = "{}",  // JSON string

    /**
     * If true, all parent variables are automatically propagated to child,
     * and all child variables are automatically propagated back to parent.
     * Overrides explicit input/output mappings.
     */
    @Column(name = "propagate_all_variables", nullable = false)
    val propagateAllVariables: Boolean = false,

    /**
     * Timestamp when this mapping was created (parent started child).
     */
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Timestamp when this mapping was last updated.
     * Updated when variable mappings are applied or status changes.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Helper method to parse input mappings as JSON for variable processing.
     * @return Map of parent variable names to child variable names
     */
    fun getInputMappingsAsMap(): Map<String, String> {
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val node = mapper.readTree(inputMappings)
            mutableMapOf<String, String>().apply {
                node.fieldNames().forEachRemaining { key ->
                    this[key] = node.get(key).asText()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Helper method to parse output mappings as JSON for variable processing.
     * @return Map of child variable names to parent variable names
     */
    fun getOutputMappingsAsMap(): Map<String, String> {
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val node = mapper.readTree(outputMappings)
            mutableMapOf<String, String>().apply {
                node.fieldNames().forEachRemaining { key ->
                    this[key] = node.get(key).asText()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

