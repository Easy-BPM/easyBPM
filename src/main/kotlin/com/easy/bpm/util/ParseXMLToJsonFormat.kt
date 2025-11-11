package com.easy.bpm.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

@Component
object ParseXMLToJsonFormat {

    fun convertXmlToInternalJson(xml: String, mapper: ObjectMapper): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xml.byteInputStream())
        doc.documentElement.normalize()

        val processNode = doc.getElementsByTagName("bpmn:process").item(0) as? Element
            ?: throw IllegalArgumentException("XML não contém um elemento <bpmn:process>")

        val processId = processNode.getAttribute("id")
        val processName = processNode.getAttribute("name")

        val nodes = mutableListOf<MutableMap<String, Any>>()
        val flows = mutableListOf<Map<String, String>>()

        // Extensão auxiliar para iterar NodeList
        fun NodeList.forEachElement(action: (Element) -> Unit) {
            for (i in 0 until this.length) {
                val node = this.item(i)
                if (node is Element) action(node)
            }
        }

        // StartEvent
        doc.getElementsByTagName("bpmn:startEvent").forEachElement { el ->
            nodes.add(
                mutableMapOf(
                    "id" to el.getAttribute("id"),
                    "type" to "StartEvent",
                    "next" to mutableListOf<String>()
                )
            )
        }

        // EndEvent
        doc.getElementsByTagName("bpmn:endEvent").forEachElement { el ->
            nodes.add(
                mutableMapOf(
                    "id" to el.getAttribute("id"),
                    "type" to "EndEvent",
                    "next" to mutableListOf<String>()
                )
            )
        }

        // UserTask
        doc.getElementsByTagName("bpmn:userTask").forEachElement { el ->
            nodes.add(
                mutableMapOf(
                    "id" to el.getAttribute("id"),
                    "type" to "UserTask",
                    "name" to el.getAttribute("name"),
                    "taskVariables" to emptyList<String>(),
                    "next" to mutableListOf<String>()
                )
            )
        }

        // ServiceTask
        doc.getElementsByTagName("bpmn:serviceTask").forEachElement { el ->
            nodes.add(
                mutableMapOf(
                    "id" to el.getAttribute("id"),
                    "type" to "ServiceTask",
                    "name" to el.getAttribute("name"),
                    "properties" to emptyMap<String, Any>(),
                    "next" to mutableListOf<String>()
                )
            )
        }

        // Gateways
        val gatewayTypes = mapOf(
            "bpmn:exclusiveGateway" to "ExclusiveGateway",
            "bpmn:parallelGateway" to "ParallelGateway",
            "bpmn:inclusiveGateway" to "InclusiveGateway"
        )

        gatewayTypes.forEach { (tag, type) ->
            doc.getElementsByTagName(tag).forEachElement { el ->
                nodes.add(
                    mutableMapOf(
                        "id" to el.getAttribute("id"),
                        "type" to type,
                        "next" to mutableListOf<String>()
                    )
                )
            }
        }

        // SequenceFlow
        doc.getElementsByTagName("bpmn:sequenceFlow").forEachElement { el ->
            val from = el.getAttribute("sourceRef")
            val to = el.getAttribute("targetRef")
            flows.add(mapOf("from" to from, "to" to to))
        }

        // Atualiza as conexões ("next") de cada nó com base nos flows
        flows.forEach { flow ->
            val fromNode = nodes.find { it["id"] == flow["from"] }
            val nextList = fromNode?.get("next") as? MutableList<String>
            nextList?.add(flow["to"]!!)
        }

        val definitionJson = mapOf(
            "id" to processId,
            "name" to processName,
            "processVariables" to emptyList<String>(),
            "nodes" to nodes,
            "flows" to flows
        )


        return mapper.writeValueAsString(
            mapOf(
                "message" to "Processo implantado com sucesso",
                "process" to mapOf(
                    "id" to processId,
                    "name" to processName,
                    "definitionJson" to definitionJson
                )
            )
        )
    }
}
