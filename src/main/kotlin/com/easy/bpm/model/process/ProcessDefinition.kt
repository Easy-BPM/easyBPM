package com.easy.bpm.model.process

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty

@Entity
data class ProcessDefinition(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(name = "process_id", nullable = false)
        @get:JsonProperty("key")
        val key: String = "",

        @get:JsonProperty("processName")
        @Column(name = "process_name")
        val processName: String? = null,

        val description: String? = null,

        val version: Int = 1,

        @Column(name = "definition_json", columnDefinition = "TEXT")
        val definitionJson: String
) {
        @get:JsonProperty("definitionXml")
        val definitionXml: String
                get() = definitionJson
}
