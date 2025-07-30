package com.easy.bpm.model.process

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id


import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.util.*

@Entity
data class ProcessDefinition(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        val name: String,

        val version: Int = 1,

        @Type(JsonBinaryType::class)
        @Column(name = "definition_json", columnDefinition = "jsonb")
        val definitionJson: String
)