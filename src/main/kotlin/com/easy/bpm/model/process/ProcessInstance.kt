package com.easy.bpm.model.process

import com.easy.bpm.enum.ProcessStatus
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDateTime


@Entity
data class ProcessInstance(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @ManyToOne
        @JoinColumn(name = "process_definition_id")
        val processDefinition: ProcessDefinition,

        var status: ProcessStatus,

        @Type(JsonBinaryType::class)
        @Column(name = "current_nodes", columnDefinition = "jsonb")
        var currentNode: List<String>? = null,

        @Type(JsonBinaryType::class)
        @Column(name = "node_history", columnDefinition = "jsonb")
        var nodeHistory: List<String> = emptyList(),

        val createdAt: LocalDateTime = LocalDateTime.now(),

        var updatedAt: LocalDateTime = LocalDateTime.now(),

        /**
         * Call Activity Support Fields (Phase 7)
         */

        /**
         * ID of parent process instance if this is a subprocess.
         * Null for root-level process instances.
         */
        @Column(name = "parent_instance_id", nullable = true)
        val parentInstanceId: Long? = null,

        /**
         * ID of the call activity node in parent process that triggered this subprocess.
         * Used to resume parent process at correct node when child completes.
         */
        @Column(name = "call_activity_node_id", nullable = true, length = 255)
        val callActivityNodeId: String? = null,

        /**
         * Nesting level in the process hierarchy.
         * 0 for root processes, incremented for each subprocess level.
         * Used to prevent infinite nesting and for optimization.
         */
        @Column(name = "nesting_level", nullable = false)
        val nestingLevel: Int = 0,

        /**
         * Node ID where parent process should resume when this child completes.
         * Populated by parent when creating this child instance.
         */
        @Column(name = "completion_node_id", nullable = true, length = 255)
        val completionNodeId: String? = null
)