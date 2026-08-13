package com.easy.bpm.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

object BpmnXmlCodec {
    private const val BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
    private const val BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"
    private const val DC_NS = "http://www.omg.org/spec/DD/20100524/DC"
    private const val DI_NS = "http://www.omg.org/spec/DD/20100524/DI"
    private const val EASY_NS = "https://easybpm.local/bpmn/extensions"

    fun parseDefinition(definition: String, objectMapper: ObjectMapper): JsonNode {
        val trimmed = definition.trimStart()
        return if (trimmed.startsWith("<")) {
            xmlToInternalJson(definition, objectMapper)
        } else {
            objectMapper.readTree(definition)
        }
    }

    fun jsonToBpmnXml(definitionJson: JsonNode, objectMapper: ObjectMapper): String {
        val json = definitionJson.takeIf { it.isObject }
            ?: throw IllegalArgumentException("Root process definition must be an object")
        val processId = json.get("processId")?.asText()
            ?: json.get("id")?.asText()
            ?: throw IllegalArgumentException("Missing 'processId'")
        val processName = json.get("processName")?.asText()
            ?: json.get("name")?.asText()
            ?: processId

        val nodes = json.get("nodes")?.takeIf { it.isArray }
            ?: throw IllegalArgumentException("Missing 'nodes'")
        val flows = json.get("flows")?.takeIf { it.isArray }
            ?: throw IllegalArgumentException("Missing 'flows'")

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<bpmn:definitions xmlns:bpmn="$BPMN_NS" xmlns:bpmndi="$BPMNDI_NS" xmlns:dc="$DC_NS" xmlns:di="$DI_NS" xmlns:easy="$EASY_NS" id="Definitions_$processId" targetNamespace="https://easybpm.local/process/$processId">""").append('\n')
            append("""  <bpmn:process id="${escapeAttr(processId)}" name="${escapeAttr(processName)}" isExecutable="true">""").append('\n')

            val metadata = buildMetadata(json, objectMapper)
            json.get("variables")?.takeIf { it.isArray && it.size() > 0 }?.let {
                append("""    <bpmn:extensionElements>""").append('\n')
                appendJsonExtension("easy:variables", it, objectMapper, 6)
                metadata?.let { metadataNode -> appendJsonExtension("easy:metadata", metadataNode, objectMapper, 6) }
                append("""    </bpmn:extensionElements>""").append('\n')
            } ?: metadata?.let {
                append("""    <bpmn:extensionElements>""").append('\n')
                appendJsonExtension("easy:metadata", it, objectMapper, 6)
                append("""    </bpmn:extensionElements>""").append('\n')
            }

            nodes.forEach { node ->
                appendBpmnNode(node, objectMapper)
            }
            flows.forEachIndexed { index, flow ->
                val id = flow.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: "Flow_${index + 1}_${flow.getText("from", "source")}_${flow.getText("to", "target")}"
                val source = flow.getText("from", "source")
                val target = flow.getText("to", "target")
                append("""    <bpmn:sequenceFlow id="${escapeAttr(id)}" sourceRef="${escapeAttr(source)}" targetRef="${escapeAttr(target)}"""")
                val condition = flow.get("condition")?.asText()?.takeIf { it.isNotBlank() }
                if (condition == null && (flow.get("waypoints") == null || flow.get("waypoints").isNull)) {
                    append("/>").append('\n')
                } else {
                    append(">").append('\n')
                    condition?.let {
                        append("""      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">${escapeText(it)}</bpmn:conditionExpression>""").append('\n')
                    }
                    flow.get("waypoints")?.takeIf { it.isArray && it.size() > 0 }?.let {
                        append("""      <bpmn:extensionElements>""").append('\n')
                        appendJsonExtension("easy:waypoints", it, objectMapper, 8)
                        append("""      </bpmn:extensionElements>""").append('\n')
                    }
                    append("""    </bpmn:sequenceFlow>""").append('\n')
                }
            }
            append("""  </bpmn:process>""").append('\n')
            appendDiagram(nodes, flows)
            append("""</bpmn:definitions>""").append('\n')
        }
    }

    fun xmlToInternalJson(xml: String, objectMapper: ObjectMapper): ObjectNode {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder().parse(xml.byteInputStream())
        doc.documentElement.normalize()

        val process = doc.getElementsByTagNameNS(BPMN_NS, "process").item(0) as? Element
            ?: throw IllegalArgumentException("BPMN XML must contain bpmn:process")
        val result = objectMapper.createObjectNode()
        result.put("processId", process.getAttribute("id"))
        result.put("processName", process.getAttribute("name").ifBlank { process.getAttribute("id") })

        extensionJson(process, "variables", objectMapper)?.let { result.set<JsonNode>("variables", it) }
            ?: result.set<ArrayNode>("variables", objectMapper.createArrayNode())
        extensionJson(process, "metadata", objectMapper)?.let { result.set<JsonNode>("metadata", it) }

        val boundsByElement = readBounds(doc)
        val nodes = objectMapper.createArrayNode()
        process.childElements().filter { it.namespaceURI == BPMN_NS && it.localName != "sequenceFlow" && it.localName != "extensionElements" }.forEach { element ->
            nodes.add(elementToNode(element, boundsByElement, objectMapper))
        }
        result.set<ArrayNode>("nodes", nodes)

        val flows = objectMapper.createArrayNode()
        process.childElements().filter { it.namespaceURI == BPMN_NS && it.localName == "sequenceFlow" }.forEach { element ->
            flows.add(elementToFlow(element, objectMapper))
        }
        result.set<ArrayNode>("flows", flows)
        hydrateNext(nodes, flows)
        return result
    }

    private fun StringBuilder.appendBpmnNode(node: JsonNode, objectMapper: ObjectMapper) {
        val tag = when (node.get("type")?.asText().orEmpty()) {
            "StartEvent" -> "startEvent"
            "EndEvent" -> "endEvent"
            "HumanTask", "UserTask" -> "userTask"
            "ServiceTask" -> "serviceTask"
            "APITask" -> "serviceTask"
            "AiTask" -> "serviceTask"
            "CodeTask" -> "serviceTask"
            "AgentProcessCall" -> "callActivity"
            "CallActivity" -> "callActivity"
            "ExclusiveGateway" -> "exclusiveGateway"
            "ParallelGateway" -> "parallelGateway"
            "InclusiveGateway" -> "inclusiveGateway"
            "TimerEvent" -> "intermediateCatchEvent"
            "MessageStartEvent" -> "startEvent"
            "MessageIntermediateCatchEvent" -> "intermediateCatchEvent"
            "MessageIntermediateThrowEvent" -> "intermediateThrowEvent"
            "ErrorBoundaryEvent" -> "boundaryEvent"
            "MessageBoundaryEvent" -> "boundaryEvent"
            "TimerBoundaryEvent" -> "boundaryEvent"
            "Participant", "Pool" -> "participant"
            else -> "task"
        }
        append("""    <bpmn:$tag id="${escapeAttr(node.get("id")?.asText().orEmpty())}" name="${escapeAttr(node.get("name")?.asText() ?: node.get("id")?.asText().orEmpty())}"""")
        node.get("attachedTo")?.asText()?.takeIf { it.isNotBlank() }?.let {
            if (tag == "boundaryEvent") append(""" attachedToRef="${escapeAttr(it)}"""")
        }
        append(">").append('\n')
        append("""      <bpmn:extensionElements>""").append('\n')
        appendJsonExtension("easy:node", node, objectMapper, 8)
        append("""      </bpmn:extensionElements>""").append('\n')
        when (node.get("type")?.asText().orEmpty()) {
            "TimerEvent", "TimerBoundaryEvent" -> append("""      <bpmn:timerEventDefinition/>""").append('\n')
            "MessageStartEvent", "MessageIntermediateCatchEvent", "MessageIntermediateThrowEvent", "MessageBoundaryEvent" -> append("""      <bpmn:messageEventDefinition/>""").append('\n')
            "ErrorBoundaryEvent" -> append("""      <bpmn:errorEventDefinition/>""").append('\n')
        }
        append("""    </bpmn:$tag>""").append('\n')
    }

    private fun buildMetadata(json: JsonNode, objectMapper: ObjectMapper): JsonNode? {
        val metadata = json.get("metadata")?.deepCopy<ObjectNode>() ?: objectMapper.createObjectNode()
        json.get("key")?.asText()?.takeIf { it.isNotBlank() }?.let { metadata.put("key", it) }
        json.get("description")?.asText()?.takeIf { it.isNotBlank() }?.let { metadata.put("description", it) }
        json.get("metadata")?.get("description")?.asText()?.takeIf { it.isNotBlank() }?.let { metadata.put("description", it) }
        return metadata.takeIf { it.size() > 0 }
    }

    private fun StringBuilder.appendDiagram(nodes: JsonNode, flows: JsonNode) {
        append("""  <bpmndi:BPMNDiagram id="BPMNDiagram_1">""").append('\n')
        append("""    <bpmndi:BPMNPlane id="BPMNPlane_1">""").append('\n')
        nodes.forEach { node ->
            val id = node.get("id")?.asText().orEmpty()
            val position = node.get("position")
            val x = position?.get("x")?.asDouble() ?: 100.0
            val y = position?.get("y")?.asDouble() ?: 100.0
            val width = node.get("width")?.asDouble() ?: 120.0
            val height = node.get("height")?.asDouble() ?: 60.0
            append("""      <bpmndi:BPMNShape id="${escapeAttr(id)}_di" bpmnElement="${escapeAttr(id)}">""").append('\n')
            append("""        <dc:Bounds x="$x" y="$y" width="$width" height="$height"/>""").append('\n')
            append("""      </bpmndi:BPMNShape>""").append('\n')
        }
        flows.forEachIndexed { index, flow ->
            val id = flow.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: "Flow_${index + 1}_${flow.getText("from", "source")}_${flow.getText("to", "target")}"
            append("""      <bpmndi:BPMNEdge id="${escapeAttr(id)}_di" bpmnElement="${escapeAttr(id)}">""").append('\n')
            val waypoints = flow.get("waypoints")?.takeIf { it.isArray && it.size() > 0 }
            if (waypoints != null) {
                waypoints.forEach { point ->
                    append("""        <di:waypoint x="${point.get("x")?.asDouble() ?: 0.0}" y="${point.get("y")?.asDouble() ?: 0.0}"/>""").append('\n')
                }
            }
            append("""      </bpmndi:BPMNEdge>""").append('\n')
        }
        append("""    </bpmndi:BPMNPlane>""").append('\n')
        append("""  </bpmndi:BPMNDiagram>""").append('\n')
    }

    private fun elementToNode(element: Element, boundsByElement: Map<String, Bounds>, objectMapper: ObjectMapper): ObjectNode {
        val extended = extensionJson(element, "node", objectMapper) as? ObjectNode
        val node = extended ?: objectMapper.createObjectNode()
        if (!node.hasNonNull("id")) node.put("id", element.getAttribute("id"))
        if (!node.hasNonNull("name")) node.put("name", element.getAttribute("name").ifBlank { element.getAttribute("id") })
        if (!node.hasNonNull("type")) node.put("type", localNameToType(element.localName))
        if (element.hasAttribute("attachedToRef") && !node.hasNonNull("attachedTo")) node.put("attachedTo", element.getAttribute("attachedToRef"))
        boundsByElement[element.getAttribute("id")]?.let { bounds ->
            val position = objectMapper.createObjectNode()
            position.put("x", bounds.x)
            position.put("y", bounds.y)
            node.set<ObjectNode>("position", position)
            node.put("width", bounds.width)
            node.put("height", bounds.height)
        }
        return node
    }

    private fun elementToFlow(element: Element, objectMapper: ObjectMapper): ObjectNode {
        val flow = objectMapper.createObjectNode()
        flow.put("id", element.getAttribute("id"))
        flow.put("from", element.getAttribute("sourceRef"))
        flow.put("to", element.getAttribute("targetRef"))
        element.childElements().firstOrNull { it.localName == "conditionExpression" }?.textContent?.takeIf { it.isNotBlank() }?.let {
            flow.put("condition", it)
        }
        extensionJson(element, "waypoints", objectMapper)?.let { flow.set<JsonNode>("waypoints", it) }
        return flow
    }

    private fun hydrateNext(nodes: ArrayNode, flows: ArrayNode) {
        val byId = nodes.associateBy { it.get("id").asText() }
        flows.forEach { flow ->
            val source = flow.get("from")?.asText() ?: return@forEach
            val target = flow.get("to")?.asText() ?: return@forEach
            val next = (byId[source] as? ObjectNode)?.withArray("next") ?: return@forEach
            next.add(target)
        }
    }

    private fun extensionJson(element: Element, localName: String, objectMapper: ObjectMapper): JsonNode? =
        element.getElementsByTagNameNS(EASY_NS, localName).item(0)?.textContent?.takeIf { it.isNotBlank() }?.let {
            objectMapper.readTree(it)
        }

    private fun readBounds(doc: org.w3c.dom.Document): Map<String, Bounds> {
        val result = mutableMapOf<String, Bounds>()
        doc.getElementsByTagNameNS(BPMNDI_NS, "BPMNShape").forEachElement { shape ->
            val elementId = shape.getAttribute("bpmnElement")
            val bounds = shape.getElementsByTagNameNS(DC_NS, "Bounds").item(0) as? Element ?: return@forEachElement
            result[elementId] = Bounds(
                bounds.getAttribute("x").toDoubleOrNull() ?: 0.0,
                bounds.getAttribute("y").toDoubleOrNull() ?: 0.0,
                bounds.getAttribute("width").toDoubleOrNull() ?: 120.0,
                bounds.getAttribute("height").toDoubleOrNull() ?: 60.0
            )
        }
        return result
    }

    private fun StringBuilder.appendJsonExtension(tag: String, node: JsonNode, objectMapper: ObjectMapper, indent: Int) {
        append(" ".repeat(indent)).append("<").append(tag).append("><![CDATA[")
        append(objectMapper.writeValueAsString(node))
        append("]]></").append(tag).append(">").append('\n')
    }

    private fun JsonNode.getText(primary: String, secondary: String): String =
        get(primary)?.asText()?.takeIf { it.isNotBlank() } ?: get(secondary)?.asText().orEmpty()

    private fun NodeList.forEachElement(action: (Element) -> Unit) {
        for (i in 0 until length) {
            val node = item(i)
            if (node is Element) action(node)
        }
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it).takeIf { node -> node.nodeType == Node.ELEMENT_NODE } as? Element }

    private fun localNameToType(localName: String): String = when (localName) {
        "startEvent" -> "StartEvent"
        "endEvent" -> "EndEvent"
        "userTask" -> "HumanTask"
        "serviceTask" -> "ServiceTask"
        "callActivity" -> "CallActivity"
        "exclusiveGateway" -> "ExclusiveGateway"
        "parallelGateway" -> "ParallelGateway"
        "inclusiveGateway" -> "InclusiveGateway"
        "boundaryEvent" -> "ErrorBoundaryEvent"
        "intermediateCatchEvent" -> "TimerEvent"
        "intermediateThrowEvent" -> "MessageIntermediateThrowEvent"
        "participant" -> "Participant"
        else -> "Task"
    }

    private fun escapeAttr(value: String): String = escapeText(value).replace("\"", "&quot;")
    private fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double)
}
