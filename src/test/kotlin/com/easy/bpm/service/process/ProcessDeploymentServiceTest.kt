package com.easy.bpm.service.process

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class ProcessDeploymentServiceTest : FunSpec() {
    init {
        val repository = mockk<ProcessDefinitionRepository>()
        val objectMapper = ObjectMapper()
        val service = ProcessDeploymentService(repository, ProcessDefinitionValidator(), objectMapper)

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should deploy first version from process metadata") {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:easy="https://easybpm.local/bpmn/extensions">
                  <bpmn:process id="order-process" name="Order" isExecutable="true">
                    <bpmn:extensionElements>
                      <easy:metadata><![CDATA[{"key":"order","description":"Order flow"}]]></easy:metadata>
                    </bpmn:extensionElements>
                  </bpmn:process>
                </bpmn:definitions>
            """.trimIndent()
            val capturedDefinition = slot<ProcessDefinition>()

            every { repository.findTopByKeyOrderByVersionDesc("order") } returns null
            every { repository.save(capture(capturedDefinition)) } answers { capturedDefinition.captured.copy(id = 10) }

            val result = service.deployProcess(xml)

            result.id shouldBe 10
            capturedDefinition.captured.key shouldBe "order"
            capturedDefinition.captured.processName shouldBe "Order"
            capturedDefinition.captured.description shouldBe "Order flow"
            capturedDefinition.captured.version shouldBe 1
            capturedDefinition.captured.definitionJson shouldBe xml
            capturedDefinition.captured.definitionJson.contains("<bpmn:process") shouldBe true
            verify { repository.save(any<ProcessDefinition>()) }
        }

        test("should deploy BPMN XML and preserve EasyBPM extensions") {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:easy="https://easybpm.local/bpmn/extensions">
                  <bpmn:process id="invoice-process" name="Invoice Process" isExecutable="true">
                    <bpmn:extensionElements>
                      <easy:variables><![CDATA[[{"name":"invoiceId","type":"string","initialValue":""}]]]></easy:variables>
                    </bpmn:extensionElements>
                    <bpmn:startEvent id="start"/>
                    <bpmn:userTask id="review" name="Review">
                      <bpmn:extensionElements>
                        <easy:node><![CDATA[{"id":"review","name":"Review","type":"HumanTask","config":{"formId":"invoice-review"},"next":["end"]}]]></easy:node>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="flow_start_review" sourceRef="start" targetRef="review"/>
                    <bpmn:sequenceFlow id="flow_review_end" sourceRef="review" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
            """.trimIndent()
            val capturedDefinition = slot<ProcessDefinition>()

            every { repository.findTopByKeyOrderByVersionDesc("invoice-process") } returns null
            every { repository.save(capture(capturedDefinition)) } answers { capturedDefinition.captured.copy(id = 20) }

            val result = service.deployProcess(xml)

            result.id shouldBe 20
            capturedDefinition.captured.key shouldBe "invoice-process"
            capturedDefinition.captured.processName shouldBe "Invoice Process"
            capturedDefinition.captured.definitionJson shouldBe xml
        }

        test("should increment existing process version") {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="order" name="Order" isExecutable="true"/>
                </bpmn:definitions>
            """.trimIndent()
            val existing = ProcessDefinition(id = 1, key = "order", processName = "Order", definitionJson = "{}", version = 4)
            val capturedDefinition = slot<ProcessDefinition>()

            every { repository.findTopByKeyOrderByVersionDesc("order") } returns existing
            every { repository.save(capture(capturedDefinition)) } answers { capturedDefinition.captured.copy(id = 2) }

            val result = service.deployProcess(xml)

            result.version shouldBe 5
            capturedDefinition.captured.version shouldBe 5
        }

        test("should reject non XML process definitions") {
            val invalidJson = """{"nodes": []}"""

            shouldThrow<IllegalArgumentException> {
                service.deployProcess(invalidJson)
            }
        }
    }
}
