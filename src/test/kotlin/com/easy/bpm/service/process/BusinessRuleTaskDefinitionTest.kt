package com.easy.bpm.service.process

import com.easy.bpm.util.BpmnXmlCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BusinessRuleTaskDefinitionTest {
    private val mapper = ObjectMapper()
    private val validator = ProcessDefinitionValidator()
    private fun xml(config: String) = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:easy="https://easybpm.local/bpmn/extensions"><process id="rules"><businessRuleTask id="evaluate"><extensionElements><easy:node><![CDATA[{"config":$config}]]></easy:node></extensionElements></businessRuleTask></process></definitions>"""

    @Test fun `native XML task and typed mappings parse with exact version`() {
        val parsed = BpmnXmlCodec.xmlToInternalJson(xml("""{"decisionKey":"eligibility","decisionVersion":2,"inputs":[{"targetName":"age","source":"static","value":21,"type":"number"}],"outputs":[{"sourceName":"approved","target":"variable","value":"approved","type":"boolean"}]}"""), mapper)
        validator.validateAndParse(parsed)
        assertEquals("BusinessRuleTask", parsed.path("nodes")[0].path("type").asText())
        assertEquals(21, parsed.path("nodes")[0].path("config").path("inputs")[0].path("value").asInt())
    }

    @Test fun `missing key invalid version and duplicate mappings rejected at deploy`() {
        val configs = listOf("{}", """{"decisionKey":"a","decisionVersion":0}""", """{"decisionKey":"a","decisionVersion":1.5}""", """{"decisionKey":"a","inputs":[{"targetName":"x","source":"static","value":1,"type":"number"},{"targetName":"x","source":"static","value":2,"type":"number"}]}""")
        configs.forEach { config -> assertThrows(IllegalArgumentException::class.java) { validator.validateAndParse(BpmnXmlCodec.xmlToInternalJson(xml(config), mapper)) } }
    }

    @Test fun `latest version omitted remains absent`() {
        val parsed = validator.validateAndParse(BpmnXmlCodec.xmlToInternalJson(xml("""{"decisionKey":"eligibility"}"""), mapper))
        assertFalse(parsed.path("nodes")[0].path("config").has("decisionVersion"))
    }
}
