package com.easy.bpm.service.process

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.nio.file.Files
import java.nio.file.Path

class ModelerQaBpmnDeploymentTest : FunSpec() {
    init {
        val objectMapper = ObjectMapper()
        val repository = mockk<ProcessDefinitionRepository>()
        val service = ProcessDeploymentService(repository, ProcessDefinitionValidator(), objectMapper)

        test("all modeler QA BPMN processes marked deployable should deploy") {
            val root = Path.of("easy-bpm-modeler", "qa-processes")
            val manifest = objectMapper.readTree(Files.readString(root.resolve("manifest.json")))
            val processFiles = manifest.get("processes")
                .filter { it.get("backendDeployCandidate")?.asBoolean() == true }
                .map { it.get("file").asText() }

            val deployedKeys = mutableListOf<String>()
            val captured = slot<ProcessDefinition>()
            every { repository.findTopByKeyOrderByVersionDesc(any()) } returns null
            every { repository.save(capture(captured)) } answers {
                deployedKeys.add(captured.captured.key)
                captured.captured.copy(id = deployedKeys.size.toLong())
            }

            processFiles.forEach { file ->
                val xml = Files.readString(root.resolve(file))
                service.deployProcess(xml)
            }

            processFiles.size shouldBe 6
            deployedKeys shouldContainExactlyInAnyOrder listOf(
                "qa_user_task_forms",
                "qa_gateway_routing",
                "qa_api_components",
                "qa_message_timer_events",
                "qa_code_task_component",
                "qa_agent_process_call"
            )
        }
    }
}
