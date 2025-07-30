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

        val createdAt: LocalDateTime = LocalDateTime.now(),

        var updatedAt: LocalDateTime = LocalDateTime.now()
)