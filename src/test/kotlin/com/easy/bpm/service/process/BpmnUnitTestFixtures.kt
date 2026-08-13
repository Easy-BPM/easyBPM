package com.easy.bpm.service.process

import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.ObjectMapper

fun unitTestBpmnXml(processId: String, nodesJson: String, flowsJson: String = "[]"): String =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:easy="https://easybpm.local/bpmn/extensions">
      <bpmn:process id="$processId" name="$processId" isExecutable="true">
        ${nodesJson.trimIndent().let(::nodesToBpmn)}
        ${flowsJson.trimIndent().let(::flowsToBpmn)}
      </bpmn:process>
    </bpmn:definitions>
    """.trimIndent()

fun internalJsonFromBpmn(xml: String, objectMapper: ObjectMapper) =
    BpmnXmlCodec.xmlToInternalJson(xml, objectMapper)

private fun nodesToBpmn(nodesJson: String): String {
    val nodes = ObjectMapper().readTree(nodesJson)
    return nodes.map { node ->
        val id = node.get("id").asText()
        val name = node.get("name")?.asText() ?: id
        val tag = when (node.get("type")?.asText()) {
            "StartEvent" -> "startEvent"
            "EndEvent" -> "endEvent"
            "HumanTask", "UserTask" -> "userTask"
            "TimerEvent" -> "intermediateCatchEvent"
            "MessageStartEvent" -> "startEvent"
            else -> "task"
        }
        val eventDefinition = when (node.get("type")?.asText()) {
            "TimerEvent" -> "\n        <bpmn:timerEventDefinition/>"
            "MessageStartEvent" -> "\n        <bpmn:messageEventDefinition/>"
            else -> ""
        }
        """<bpmn:$tag id="$id" name="$name"><bpmn:extensionElements><easy:node><![CDATA[${node.toString()}]]></easy:node></bpmn:extensionElements>$eventDefinition</bpmn:$tag>"""
    }.joinToString("\n        ")
}

private fun flowsToBpmn(flowsJson: String): String {
    val flows = ObjectMapper().readTree(flowsJson)
    return flows.mapIndexed { index, flow ->
        val source = flow.get("from")?.asText() ?: flow.get("source")?.asText().orEmpty()
        val target = flow.get("to")?.asText() ?: flow.get("target")?.asText().orEmpty()
        val id = flow.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: "Flow_${index + 1}_${source}_$target"
        """<bpmn:sequenceFlow id="$id" sourceRef="$source" targetRef="$target"/>"""
    }.joinToString("\n        ")
}
