package com.easy.bpm.service.variable

import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.HistoricProcessVariable
import com.easy.bpm.model.variable.HistoricTaskVariable
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.variable.TaskVariable
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.repository.variable.HistoricTaskVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.easy.bpm.enum.ProcessStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HistoricVariableArchiverTest : FunSpec() {
    init {
        val processVariableRepository = mockk<ProcessVariableRepository>()
        val taskVariableRepository = mockk<TaskVariableRepository>()
        val historicProcessVariableRepository = mockk<HistoricProcessVariableRepository>()
        val historicTaskVariableRepository = mockk<HistoricTaskVariableRepository>()
        val archiver = HistoricVariableArchiver(
            processVariableRepository,
            taskVariableRepository,
            historicProcessVariableRepository,
            historicTaskVariableRepository
        )
        val objectMapper = ObjectMapper()

        test("should archive process variables before deleting process runtime rows") {
            val processVariable = ProcessVariable(
                id = 11,
                processInstanceId = 100,
                name = "approved",
                value = objectMapper.valueToTree(true)
            )

            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(processVariable)
            every { historicProcessVariableRepository.findByProcessInstanceId(100) } returns emptyList()
            every { historicProcessVariableRepository.deleteByProcessInstanceId(100) } returns 0
            every { historicProcessVariableRepository.flush() } returns Unit
            every { historicProcessVariableRepository.saveAll(any<Iterable<HistoricProcessVariable>>()) } answers {
                firstArg<Iterable<HistoricProcessVariable>>().toList()
            }
            every { processVariableRepository.deleteByProcessInstanceId(100) } returns 1

            val counts = archiver.archiveProcessInstanceVariables(100)

            counts.processVariables shouldBe 1
            counts.taskVariables shouldBe 0
            verify {
                historicProcessVariableRepository.saveAll(match<Iterable<HistoricProcessVariable>> {
                    val archived = it.single()
                    archived.id == 11L && archived.processInstanceId == 100L
                })
                processVariableRepository.deleteByProcessInstanceId(100)
            }
        }

        test("should archive a completed task and remove only that task runtime variables") {
            val definition = ProcessDefinition(id = 1, key = "approval", definitionJson = "{}")
            val instance = ProcessInstance(id = 100, processDefinition = definition, status = ProcessStatus.ACTIVE)
            val task = Task(id = 200, processInstanceId = instance.id, nodeId = "review")
            val taskVariable = TaskVariable(
                id = 33,
                taskId = task.id,
                processInstanceId = task.processInstanceId,
                name = "decision",
                value = objectMapper.valueToTree("approve")
            )

            every { taskVariableRepository.findByTaskId(task.id) } returns listOf(taskVariable)
            every { historicTaskVariableRepository.saveAll(any<Iterable<HistoricTaskVariable>>()) } answers {
                firstArg<Iterable<HistoricTaskVariable>>().toList()
            }
            every { taskVariableRepository.deleteByTaskId(task.id) } returns 1

            val archived = archiver.archiveTaskVariables(task)

            archived shouldBe 1
            verify {
                historicTaskVariableRepository.saveAll(match<Iterable<HistoricTaskVariable>> {
                    val archivedTaskVariable = it.single()
                    archivedTaskVariable.id == 33L &&
                        archivedTaskVariable.taskId == 200L &&
                        archivedTaskVariable.processInstanceId == 100L
                })
                taskVariableRepository.deleteByTaskId(task.id)
            }
        }
    }
}
