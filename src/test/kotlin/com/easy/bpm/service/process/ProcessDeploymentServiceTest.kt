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
        val service = ProcessDeploymentService(repository, ProcessDefinitionValidator())
        val objectMapper = ObjectMapper()

        beforeEach {
            io.mockk.clearAllMocks()
        }

        test("should deploy first version from process metadata") {
            val json = objectMapper.readTree(
                """
                {
                  "processId": "order-process",
                  "key": "order",
                  "name": "Order",
                  "metadata": {"description": "Order flow"},
                  "nodes": [],
                  "flows": []
                }
                """.trimIndent()
            )
            val capturedDefinition = slot<ProcessDefinition>()

            every { repository.findTopByKeyOrderByVersionDesc("order") } returns null
            every { repository.save(capture(capturedDefinition)) } answers { capturedDefinition.captured.copy(id = 10) }

            val result = service.deployProcess(json)

            result.id shouldBe 10
            capturedDefinition.captured.key shouldBe "order"
            capturedDefinition.captured.processName shouldBe "Order"
            capturedDefinition.captured.description shouldBe "Order flow"
            capturedDefinition.captured.version shouldBe 1
            verify { repository.save(any<ProcessDefinition>()) }
        }

        test("should increment existing process version") {
            val json = objectMapper.readTree(
                """
                {
                  "processId": "order",
                  "nodes": [],
                  "flows": []
                }
                """.trimIndent()
            )
            val existing = ProcessDefinition(id = 1, key = "order", processName = "Order", definitionJson = "{}", version = 4)
            val capturedDefinition = slot<ProcessDefinition>()

            every { repository.findTopByKeyOrderByVersionDesc("order") } returns existing
            every { repository.save(capture(capturedDefinition)) } answers { capturedDefinition.captured.copy(id = 2) }

            val result = service.deployProcess(json)

            result.version shouldBe 5
            capturedDefinition.captured.version shouldBe 5
        }

        test("should reject invalid process definitions") {
            val invalidJson = objectMapper.readTree("""{"nodes": []}""")

            shouldThrow<IllegalArgumentException> {
                service.deployProcess(invalidJson)
            }
        }
    }
}
