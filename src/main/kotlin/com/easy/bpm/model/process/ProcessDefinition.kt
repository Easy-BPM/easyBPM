package com.easy.bpm.model.process

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.hibernate.annotations.Type

@Entity
data class ProcessDefinition(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(name = "process_key", nullable = false)
        @get:JsonProperty("key")
        val key: String = "",

        val name: String,

        val description: String? = null,

        val version: Int = 1,

        @Type(JsonBinaryType::class)
        @Column(name = "definition_json", columnDefinition = "jsonb")
        val definitionJson: String
)