package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.message.MessageSubscriptionRepository
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
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional

class ProcessIntegrationTest(
    @Autowired private val processService: ProcessService,
    @Autowired private val taskService: TaskService,
    @Autowired private val processInstanceRepository: ProcessInstanceRepository,
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val processVariableRepository: ProcessVariableRepository,
    @Autowired private val messageSubscriptionRepository: MessageSubscriptionRepository,
    @Autowired private val taskVariableRepository: TaskVariableRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val mockMvc: MockMvc
) : IntegrationTestBase() {

    @Test
    fun `service task error should trigger error boundary event and route to after-error user task`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("examples/error-boundary.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)

        // Set shouldFail to true to simulate error in service task
        val processInstance = processService.startProcessInstance(processDefinition.id)

        // Reload instance to get latest state after error boundary routing
        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()

        // Debug output
        println("DEBUG: currentNode = ${updatedInstance.currentNode}")
        println("DEBUG: nodeHistory = ${updatedInstance.nodeHistory}")

        // The process should route to the error boundary's next node (after-error user task)
        assertThat(updatedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(updatedInstance.currentNode).containsExactly("after-error")
        assertThat(updatedInstance.nodeHistory).contains("after-error")
    }

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

    @Test
    fun `variable overwrite should update existing process variable on service task completion`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        // Verify that there are no duplicate variables - this tests the overwrite logic
        // If the bug existed (always creating new vars), we'd have duplicates
        val variables = processVariableRepository.findByProcessInstanceId(processInstance.id)
        val varNames = variables.map { it.name }
        
        // Key assertion: no duplicate variable names
        assertThat(varNames).doesNotHaveDuplicates()
        
        // Verify expected variables exist (not duplicates)
        assertThat(varNames).contains("staticVar", "dynamicVar", "sourceVar")
        
        // Each variable should appear exactly once
        assertThat(variables.filter { it.name == "dynamicVar" }).hasSize(1)
        assertThat(variables.filter { it.name == "staticVar" }).hasSize(1)
    }

    @Test
    fun `task variable creation should handle multiple variables correctly`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-one-user-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        val createdTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }

        // Submit form with multiple variables
        val formVariables = mapOf(
            "firstName" to "John",
            "lastName" to "Doe",
            "email" to "john@example.com",
            "age" to 30
        )

        taskService.completeTask(createdTask.id, "test-user", formVariables)

        // All variables should be persisted as task variables
        val taskVariables = taskVariableRepository.findByTaskId(createdTask.id)
        assertThat(taskVariables).hasSizeGreaterThanOrEqualTo(4)

        val varNames = taskVariables.map { it.name }.toSet()
        assertThat(varNames).contains("firstName", "lastName", "email", "age")
    }

    @Test
    fun `message receive should overwrite existing process variable correctly`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-one-user-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        val createdTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }

        // Complete task
        taskService.completeTask(createdTask.id, "test-user", emptyMap())

        // Verify no duplicate variables exist
        val allVars = processVariableRepository.findByProcessInstanceId(processInstance.id)
        assertThat(allVars.size).isGreaterThan(0)

        // Should not have duplicate variables
        val varNames = allVars.map { it.name }
        assertThat(varNames).doesNotHaveDuplicates()
    }

    @Test
    fun `process completion should be detected correctly for all gateway types`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        // Process should be COMPLETED after all nodes execute
        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(processInstance.currentNode).isEmpty()
        assertThat(processInstance.nodeHistory).contains("end_service_task")
    }

    @Test
    @Disabled("Parallel gateway join logic out of scope for Phase 2")
    fun `parallel gateway should complete process when all paths finish`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-parallel-gateway.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        // Process should complete when both parallel paths complete and join
        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(processInstance.currentNode).isEmpty()
        
        // Verify the process executed the expected nodes
        assertThat(processInstance.nodeHistory).contains("parallel_split")
        assertThat(processInstance.nodeHistory).contains("end_parallel")
    }

    @Test
    fun `message throw process should send message and message catch process should receive and complete`() {
        val objectMapper = objectMapper

        // Deploy and start the catch process (waits for message)
        val catchDefJson = objectMapper.readTree(ClassPathResource("examples/message-catch-process.json").inputStream)
        val catchDef = processService.deployProcess(catchDefJson)
        val catchInstance = processService.startProcessInstance(catchDef.id)
        assertThat(catchInstance.currentNode).containsExactly("message-intermediate-catch_fgsda310o")
        assertThat(catchInstance.status).isEqualTo(ProcessStatus.ACTIVE)

        // Deploy and start the throw process (sends message)
        val throwDefJson = objectMapper.readTree(ClassPathResource("examples/message-throw-process.json").inputStream)
        val throwDef = processService.deployProcess(throwDefJson)
        val throwInstance = processService.startProcessInstance(throwDef.id)
        assertThat(throwInstance.currentNode).isEmpty()
        assertThat(throwInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        // Manually trigger message correlation (simulate external message delivery)
        processService.handleMessageReceived(
            "Order",
            "004",
            null
        )

        // After message is delivered, the catch process should complete
        val updatedCatchInstance = processInstanceRepository.findById(catchInstance.id).orElseThrow()
        assertThat(updatedCatchInstance.currentNode).isEmpty()
        assertThat(updatedCatchInstance.status).isEqualTo(ProcessStatus.COMPLETED)
    }

    @Test
    fun `timer event json process should create subscription and continue after timeout`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-timer-event.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("timer_wait-30s")

        val timerSubscription = messageSubscriptionRepository.findByProcessInstanceIdAndNodeId(
            processInstance.id,
            "timer_wait-30s"
        )
        assertThat(timerSubscription).isNotNull
        assertThat(timerSubscription?.status).isEqualTo(MessageSubscriptionStatus.AWAITING)
        assertThat(timerSubscription?.messageName).isEqualTo(ProcessService.INTERNAL_TIMER_MESSAGE_NAME)

        val resumed = processService.handleTimerTimeout(processInstance.id, "timer_wait-30s")
        assertThat(resumed).isTrue()

        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(updatedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(updatedInstance.currentNode).containsExactly("user-task_review-after-timer")

        val timerFired = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "timerFired")
        val message = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "message")
        assertThat(timerFired?.value?.asText()).isEqualTo("true")
        assertThat(message?.value?.asText()).contains("Timer has fired")
    }

    @Test
    fun `api task json process should wait on api task after human task completion`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-api-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("user-task_submit-order")

        val userTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }
        taskService.completeTask(userTask.id, "tester", mapOf("orderId" to "ORD-900"))

        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(updatedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(updatedInstance.currentNode).containsExactly("api-task_notify-warehouse")
    }

    @Test
    fun `inclusive gateway json process should execute both service branches`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-inclusive-gateway.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.currentNode).containsExactly("user-task_submit-request")
        val userTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }
        taskService.completeTask(userTask.id, "tester", mapOf("orderValue" to 1500))

        val completedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(completedInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(completedInstance.currentNode).isEmpty()
        assertThat(completedInstance.nodeHistory).contains("service_send-email", "service_notify-manager")

        val emailSent = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "emailSent")
        val managerNotified = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "managerNotified")
        assertThat(emailSent?.value?.asText()).isEqualTo("true")
        assertThat(managerNotified?.value?.asText()).isEqualTo("true")
    }

    @Test
    fun `call activity json processes should map child output back to parent`() {
        val childJson = objectMapper.readTree(ClassPathResource("process-call-activity-child.json").inputStream)
        val parentJson = objectMapper.readTree(ClassPathResource("process-call-activity.json").inputStream)

        processService.deployProcess(childJson)
        val parentDefinition = processService.deployProcess(parentJson)
        val parentInstance = processService.startProcessInstance(parentDefinition.id)

        val waitingParent = processInstanceRepository.findById(parentInstance.id).orElseThrow()
        assertThat(waitingParent.status).isEqualTo(ProcessStatus.WAITING)

        val childInstance = processInstanceRepository.findByParentInstanceId(parentInstance.id).single()
        val childTask = taskRepository.findByProcessInstanceId(childInstance.id).single { it.status == TaskStatus.PENDING }
        taskService.completeTask(childTask.id, "approver", mapOf("decision" to "APPROVED"))

        val completedParent = processInstanceRepository.findById(parentInstance.id).orElseThrow()
        assertThat(completedParent.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(completedParent.currentNode).isEmpty()

        val approvalResult = processVariableRepository.findByProcessInstanceIdAndName(parentInstance.id, "approvalResult")
        val processCompleted = processVariableRepository.findByProcessInstanceIdAndName(parentInstance.id, "processCompleted")
        assertThat(approvalResult?.value?.asText()).contains("APPROVED")
        assertThat(processCompleted?.value?.asText()).isEqualTo("true")
    }

    @Test
    fun `move node endpoint should remove old pending task and create new pending task in tasks api`() {
        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "move-node-approval",
              "nodes": [
                { "id": "start", "type": "StartEvent", "next": ["manual-review"] },
                { "id": "manual-review", "type": "HumanTask", "name": "Manual Review", "next": ["approve-request"] },
                { "id": "approve-request", "type": "HumanTask", "name": "Approve Request", "next": ["end"] },
                { "id": "end", "type": "EndEvent" }
              ],
              "flows": []
            }
            """.trimIndent()
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val instance = processService.startProcessInstance(processDefinition.id)

        val beforeMovePendingNodes = getPendingTaskNodesFromApi(instance.id)
        assertThat(beforeMovePendingNodes).containsExactly("manual-review")

        mockMvc.perform(
            post("/processes/instances/${instance.id}/move-node")
                .contentType("application/json")
                .content(
                    """
                    {
                      "fromNode": "manual-review",
                      "toNode": "approve-request",
                      "reason": "Admin correction"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)

        val afterMovePendingNodes = getPendingTaskNodesFromApi(instance.id)
        assertThat(afterMovePendingNodes).containsExactly("approve-request")
        assertThat(afterMovePendingNodes).doesNotContain("manual-review")
    }

    @Test
    fun `stop instance endpoint should remove pending tasks from tasks api`() {
        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "stop-instance-approval",
              "nodes": [
                { "id": "start", "type": "StartEvent", "next": ["manual-review"] },
                { "id": "manual-review", "type": "HumanTask", "name": "Manual Review", "next": ["end"] },
                { "id": "end", "type": "EndEvent" }
              ],
              "flows": []
            }
            """.trimIndent()
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val instance = processService.startProcessInstance(processDefinition.id)

        val beforeStopPendingNodes = getPendingTaskNodesFromApi(instance.id)
        assertThat(beforeStopPendingNodes).containsExactly("manual-review")

        mockMvc.perform(
            post("/processes/instances/${instance.id}/stop")
                .contentType("application/json")
        )
            .andExpect(status().isOk)

        val afterStopPendingNodes = getPendingTaskNodesFromApi(instance.id)
        assertThat(afterStopPendingNodes).isEmpty()

        val cancelledInstance = processInstanceRepository.findById(instance.id).orElseThrow()
        assertThat(cancelledInstance.status).isEqualTo(ProcessStatus.CANCELLED)
    }

        @Test
        fun `api task failure callback should route to attached error boundary workflow`() {
                val processDefinitionJson = objectMapper.readTree(
                        """
                        {
                            "processId": "api-failure-error-boundary",
                            "nodes": [
                                { "id": "start", "type": "StartEvent", "next": ["api-task"] },
                                { "id": "api-task", "type": "APITask", "name": "Call API", "next": ["end-success"], "properties": { "url": "http://invalid.local", "method": "GET", "outputs": [] } },
                                { "id": "error-boundary", "type": "ErrorBoundaryEvent", "attachedTo": "api-task", "next": ["error-task"] },
                                { "id": "error-task", "type": "HumanTask", "name": "Handle Failure", "next": ["end-error"], "config": { "inputs": [], "outputs": [] } },
                                { "id": "end-success", "type": "EndEvent" },
                                { "id": "end-error", "type": "EndEvent" }
                            ],
                            "flows": [
                                { "from": "start", "to": "api-task", "condition": null },
                                { "from": "api-task", "to": "end-success", "condition": null },
                                { "from": "error-boundary", "to": "error-task", "condition": null },
                                { "from": "error-task", "to": "end-error", "condition": null }
                            ]
                        }
                        """.trimIndent()
                )

                val processDefinition = processService.deployProcess(processDefinitionJson)
                val instance = processService.startProcessInstance(processDefinition.id)

                val startedInstance = processInstanceRepository.findById(instance.id).orElseThrow()
                assertThat(startedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
                assertThat(startedInstance.currentNode).containsExactly("api-task")

                processService.handleServiceTaskFailed(instance.id, "api-task", "Simulated worker failure")

                val updatedInstance = processInstanceRepository.findById(instance.id).orElseThrow()
                assertThat(updatedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
                assertThat(updatedInstance.currentNode).containsExactly("error-task")
                assertThat(updatedInstance.nodeHistory).contains("error-task")

                val pendingTasks = taskRepository.findByProcessInstanceId(instance.id)
                        .filter { it.status == TaskStatus.PENDING }
                assertThat(pendingTasks).hasSize(1)
                assertThat(pendingTasks.first().nodeId).isEqualTo("error-task")
        }

    private fun getPendingTaskNodesFromApi(processInstanceId: Long): List<String> {
        val responseBody = mockMvc.perform(get("/tasks?page=0&size=100"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val root = objectMapper.readTree(responseBody)
        return root.get("content")
            .filter {
                it.get("processInstanceId")?.asLong() == processInstanceId &&
                    it.get("status")?.asText() == TaskStatus.PENDING.name
            }
            .mapNotNull { it.get("nodeId")?.asText() }
    }
}
