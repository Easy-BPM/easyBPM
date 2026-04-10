package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.ProcessService
import com.easy.bpm.service.TaskService
import com.easy.bpm.messaging.RabbitPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
class ProcessIntegrationTest(
    @Autowired private val processService: ProcessService,
    @Autowired private val taskService: TaskService,
    @Autowired private val processInstanceRepository: ProcessInstanceRepository,
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val processVariableRepository: ProcessVariableRepository,
    @Autowired private val taskVariableRepository: TaskVariableRepository,
    @Autowired private val objectMapper: ObjectMapper
) {

    @MockBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Test
    fun `start user task process should create task variable and complete process`() {
        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "easyProcessOneUserTask",
              "metadata": {
                "exportedAt": "2026-04-10T20:04:16.022Z",
                "version": "1.0"
              },
              "variables": [
                {
                  "name": "var1",
                  "type": "string",
                  "initialValue": "\"test\""
                },
                {
                  "name": "var_2",
                  "type": "string",
                  "initialValue": ""
                }
              ],
              "nodes": [
                {
                  "id": "start_y8xubc4hb",
                  "name": "New start",
                  "type": "StartEvent",
                  "position": {
                    "x": 120,
                    "y": 210
                  },
                  "next": [
                    "user-task_mxtpi3bf9"
                  ]
                },
                {
                  "id": "user-task_mxtpi3bf9",
                  "name": "New user task",
                  "type": "UserTask",
                  "position": {
                    "x": 310,
                    "y": 200
                  },
                  "next": [
                    "end_mh30nsi9i"
                  ],
                  "config": {
                    "inputs": [
                      {
                        "targetName": "InternalVAR",
                        "type": "string",
                        "source": "variable",
                        "value": "var1"
                      }
                    ],
                    "outputs": [
                      {
                        "sourceName": "InternalVAR",
                        "type": "string",
                        "target": "variable",
                        "value": "var_2"
                      }
                    ]
                  }
                },
                {
                  "id": "end_mh30nsi9i",
                  "name": "New end",
                  "type": "EndEvent",
                  "position": {
                    "x": 550,
                    "y": 210
                  },
                  "next": []
                }
              ],
              "flows": [
                {
                  "from": "start_y8xubc4hb",
                  "to": "user-task_mxtpi3bf9",
                  "condition": null
                },
                {
                  "from": "user-task_mxtpi3bf9",
                  "to": "end_mh30nsi9i",
                  "condition": null
                }
              ]
            }
            """
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("user-task_mxtpi3bf9")

        val createdTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }
        assertThat(createdTask.status).isEqualTo(TaskStatus.PENDING)

        val taskVariables = taskVariableRepository.findByTaskId(createdTask.id)
        assertThat(taskVariables).anySatisfy { taskVariable ->
            assertThat(taskVariable.name).isEqualTo("InternalVAR")
            assertThat(taskVariable.value.asText()).isEqualTo("\"test\"")
        }

        val processVariables = processVariableRepository.findByProcessInstanceId(processInstance.id)
        assertThat(processVariables).anySatisfy { processVariable ->
            if (processVariable.name == "var1") {
                assertThat(processVariable.value.asText()).isEqualTo("\"test\"")
            }
        }

        taskService.completeTask(createdTask.id, "test-user", emptyMap())

        val completedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(completedInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        val completedTask = taskRepository.findById(createdTask.id).orElseThrow()
        assertThat(completedTask.status).isEqualTo(TaskStatus.COMPLETED)

        val finalProcessVariable = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "var_2")
        assertThat(finalProcessVariable).isNotNull
        assertThat(finalProcessVariable?.value?.asText()).isEqualTo("\"test\"")
    }
}
