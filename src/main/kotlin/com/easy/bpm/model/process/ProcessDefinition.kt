package com.easy.bpm.model.process

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.hibernate.annotations.Type

@Entity
data class ProcessDefinition(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(name = "tenant_id", nullable = false, length = 100)
        val tenantId: String = "default",

        @Column(name = "process_id", nullable = false)
        @get:JsonProperty("key")
        val key: String = "",

        @get:JsonProperty("processName")
        @Column(name = "process_name")
        val processName: String? = null,

        val description: String? = null,

        val version: Int = 1,

        @Type(JsonBinaryType::class)
        @Column(name = "definition_json", columnDefinition = "jsonb")
        val definitionJson: String
)
