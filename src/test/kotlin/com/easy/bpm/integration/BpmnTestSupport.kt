package com.easy.bpm.integration

import com.fasterxml.jackson.databind.JsonNode

private const val BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
private const val BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"
private const val DC_NS = "http://www.omg.org/spec/DD/20100524/DC"
private const val DI_NS = "http://www.omg.org/spec/DD/20100524/DI"
private const val EASY_NS = "https://easybpm.local/bpmn/extensions"

fun legacyJsonFixtureToBpmnXml(definition: JsonNode): String {
    val processId = definition.get("processId")?.asText()
        ?: definition.get("id")?.asText()
        ?: error("Missing processId")
    val processName = definition.get("processName")?.asText()
        ?: definition.get("name")?.asText()
        ?: processId
    val nodes = definition.get("nodes")?.takeIf { it.isArray } ?: error("Missing nodes")
    val flows = definition.get("flows")?.takeIf { it.isArray } ?: error("Missing flows")

    return buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<bpmn:definitions xmlns:bpmn="$BPMN_NS" xmlns:bpmndi="$BPMNDI_NS" xmlns:dc="$DC_NS" xmlns:di="$DI_NS" xmlns:easy="$EASY_NS" id="Definitions_${processId.escapeXmlAttr()}" targetNamespace="https://easybpm.local/process/${processId.escapeXmlAttr()}">""").append('\n')
        append("""  <bpmn:process id="${processId.escapeXmlAttr()}" name="${processName.escapeXmlAttr()}" isExecutable="true">""").append('\n')
        definition.get("variables")?.takeIf { it.isArray && it.size() > 0 }?.let {
            append("""    <bpmn:extensionElements>""").append('\n')
            appendJsonExtension("easy:variables", it, 6)
            append("""    </bpmn:extensionElements>""").append('\n')
        }
        nodes.forEach { appendNode(it) }
        flows.forEachIndexed { index, flow -> appendFlow(flow, index) }
        append("""  </bpmn:process>""").append('\n')
        append("""</bpmn:definitions>""").append('\n')
    }
}

private fun StringBuilder.appendNode(node: JsonNode) {
    val type = node.get("type")?.asText().orEmpty()
    val tag = when (type) {
        "StartEvent" -> "startEvent"
        "EndEvent" -> "endEvent"
        "HumanTask", "UserTask" -> "userTask"
        "ServiceTask", "APITask", "AiTask", "CodeTask" -> "serviceTask"
        "AgentProcessCall", "CallActivity" -> "callActivity"
        "ExclusiveGateway" -> "exclusiveGateway"
        "ParallelGateway" -> "parallelGateway"
        "InclusiveGateway" -> "inclusiveGateway"
        "TimerEvent", "MessageIntermediateCatchEvent" -> "intermediateCatchEvent"
        "MessageIntermediateThrowEvent" -> "intermediateThrowEvent"
        "MessageStartEvent" -> "startEvent"
        "ErrorBoundaryEvent", "MessageBoundaryEvent", "TimerBoundaryEvent" -> "boundaryEvent"
        else -> "task"
    }
    val id = node.get("id")?.asText().orEmpty()
    val name = node.get("name")?.asText() ?: id
    append("""    <bpmn:$tag id="${id.escapeXmlAttr()}" name="${name.escapeXmlAttr()}"""")
    node.get("attachedTo")?.asText()?.takeIf { it.isNotBlank() }?.let {
        if (tag == "boundaryEvent") append(""" attachedToRef="${it.escapeXmlAttr()}"""")
    }
    append(">").append('\n')
    append("""      <bpmn:extensionElements>""").append('\n')
    appendJsonExtension("easy:node", node, 8)
    append("""      </bpmn:extensionElements>""").append('\n')
    when (type) {
        "TimerEvent", "TimerBoundaryEvent" -> append("""      <bpmn:timerEventDefinition/>""").append('\n')
        "MessageStartEvent", "MessageIntermediateCatchEvent", "MessageIntermediateThrowEvent", "MessageBoundaryEvent" -> append("""      <bpmn:messageEventDefinition/>""").append('\n')
        "ErrorBoundaryEvent" -> append("""      <bpmn:errorEventDefinition/>""").append('\n')
    }
    append("""    </bpmn:$tag>""").append('\n')
}

private fun StringBuilder.appendFlow(flow: JsonNode, index: Int) {
    val source = flow.getText("from", "source")
    val target = flow.getText("to", "target")
    val id = flow.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: "Flow_${index + 1}_${source}_$target"
    append("""    <bpmn:sequenceFlow id="${id.escapeXmlAttr()}" sourceRef="${source.escapeXmlAttr()}" targetRef="${target.escapeXmlAttr()}"""")
    val condition = flow.get("condition")?.asText()?.takeIf { it.isNotBlank() }
    if (condition == null) {
        append("/>").append('\n')
    } else {
        append(">").append('\n')
        append("""      <bpmn:conditionExpression>${condition.escapeXmlText()}</bpmn:conditionExpression>""").append('\n')
        append("""    </bpmn:sequenceFlow>""").append('\n')
    }
}

private fun StringBuilder.appendJsonExtension(tag: String, node: JsonNode, indent: Int) {
    append(" ".repeat(indent)).append("<").append(tag).append("><![CDATA[")
    append(node.toString())
    append("]]></").append(tag).append(">").append('\n')
}

private fun JsonNode.getText(primary: String, secondary: String): String =
    get(primary)?.asText()?.takeIf { it.isNotBlank() } ?: get(secondary)?.asText().orEmpty()

private fun String.escapeXmlAttr(): String = escapeXmlText().replace("\"", "&quot;")

private fun String.escapeXmlText(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
