package com.easy.bpm.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object ProcessGraphUtil {

    fun buildGraph(definitionJson: String, objectMapper: ObjectMapper): Map<String, List<String>> {
        val graph = mutableMapOf<String, List<String>>()
        val nodes: JsonNode = objectMapper.readTree(definitionJson).get("nodes")
        for (node in nodes) {
            val id = node.get("id").asText()
            val next = node.get("next")?.map { it.asText() } ?: emptyList()
            graph[id] = next
        }
        return graph
    }
}
