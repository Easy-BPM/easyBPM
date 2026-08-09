package com.easy.bpm.model.variable

import com.fasterxml.jackson.databind.JsonNode
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.annotations.Type

@Entity
data class HistoricProcessVariable(
    @Id
    val id: Long,

    val processInstanceId: Long,

    val name: String,

    @Type(JsonBinaryType::class)
    @Column(columnDefinition = "jsonb")
    var value: JsonNode
)
