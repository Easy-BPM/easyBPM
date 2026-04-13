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
import org.springframework.core.io.ClassPathResource
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
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-one-user-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("user-task_mxtpi3bf9")
        assertThat(processInstance.nodeHistory).containsExactly("user-task_mxtpi3bf9")

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
        assertThat(completedInstance.nodeHistory).contains("user-task_mxtpi3bf9")

        val completedTask = taskRepository.findById(createdTask.id).orElseThrow()
        assertThat(completedTask.status).isEqualTo(TaskStatus.COMPLETED)

        val finalProcessVariable = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "var_2")
        assertThat(finalProcessVariable).isNotNull
        assertThat(finalProcessVariable?.value?.asText()).isEqualTo("\"test\"")
    }


    @Test
    fun `exclusive gateway should route to conditional path when condition is met`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-exclusive-gateway.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("user-task_1c50w5biq")
        assertThat(processInstance.nodeHistory).containsExactly("user-task_1c50w5biq")

        // Complete user task (input mapping sets logicalCondition to true, output mapping copies to process variable)
        val task = taskRepository.findAll().first { it.processInstanceId == processInstance.id }
        taskService.completeTask(task.id, "test-user", emptyMap())

        // Process should have moved to the conditional service task path
        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(updatedInstance.nodeHistory).containsExactly("user-task_1c50w5biq", "service-task_s53rkrxeh")

        // Verify logicalCondition variable was set to true
        val logicalConditionVar = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "logicalCondition")
        assertThat(logicalConditionVar).isNotNull
        assertThat(logicalConditionVar?.value?.asText()).isEqualTo("true")

        // Simulate service task completion
        processService.handleServiceTaskCompleted(processInstance.id, "service-task_s53rkrxeh", emptyMap())

        // Process should complete
        val completedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(completedInstance.status).isEqualTo(ProcessStatus.COMPLETED)
    }

    @Test
    fun `service task node should set process variables and continue to next step`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(processInstance.currentNode).isEmpty()
        assertThat(processInstance.nodeHistory).containsExactly("service_task_1", "end_service_task")

        // Verify static variable was set
        val staticVar = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "staticVar")
        assertThat(staticVar).isNotNull
        assertThat(staticVar?.value?.asText()).isEqualTo("\"static_value\"")

        // Verify dynamic variable was set (copied from sourceVar)
        val dynamicVar = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "dynamicVar")
        assertThat(dynamicVar).isNotNull
        assertThat(dynamicVar?.value?.asText()).isEqualTo("\"initial_value\"")
    }

    @Test
    fun `service task node should support both static and variable assignment in same node`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        // Retrieve all process variables for the instance
        val variables = processVariableRepository.findByProcessInstanceId(processInstance.id)

        // Should have sourceVar, staticVar, and dynamicVar
        assertThat(variables).hasSize(3)

        // Check sourceVar is unchanged (initial value)
        val sourceVar = variables.first { it.name == "sourceVar" }
        assertThat(sourceVar.value.asText()).isEqualTo("\"initial_value\"")

        // Check staticVar was set
        val staticVar = variables.first { it.name == "staticVar" }
        assertThat(staticVar.value.asText()).isEqualTo("\"static_value\"")

        // Check dynamicVar was set from sourceVar
        val dynamicVar = variables.first { it.name == "dynamicVar" }
        assertThat(dynamicVar.value.asText()).isEqualTo("\"initial_value\"")
    }
}
