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
    private const val EASY_NS = "https://easybpm.local/bpmn/extensions"

    fun parseDefinition(definition: String, objectMapper: ObjectMapper): JsonNode {
        val trimmed = definition.trimStart()
        require(trimmed.startsWith("<")) {
            "Process definitions must be BPMN XML"
        }
        return xmlToInternalJson(definition, objectMapper)
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
    private data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double)
}
