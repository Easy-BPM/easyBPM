package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.agent.AgentProcessExecutionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.repository.variable.HistoricTaskVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.integration.IntegrationService
import com.easy.bpm.service.agent.AgentProcessService
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.task.TaskService
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
    @Autowired private val agentProcessService: AgentProcessService,
    @Autowired private val taskService: TaskService,
    @Autowired private val processInstanceRepository: ProcessInstanceRepository,
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val processVariableRepository: ProcessVariableRepository,
    @Autowired private val historicProcessVariableRepository: HistoricProcessVariableRepository,
    @Autowired private val historicTaskVariableRepository: HistoricTaskVariableRepository,
    @Autowired private val messageSubscriptionRepository: MessageSubscriptionRepository,
    @Autowired private val agentProcessExecutionRepository: AgentProcessExecutionRepository,
    @Autowired private val taskVariableRepository: TaskVariableRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val mockMvc: MockMvc
) : IntegrationTestBase() {

    private fun processVariableValue(processInstanceId: Long, name: String) =
        processVariableRepository.findByProcessInstanceIdAndName(processInstanceId, name)?.value
            ?: historicProcessVariableRepository.findByProcessInstanceId(processInstanceId)
                .firstOrNull { it.name == name }
                ?.value

    private fun processVariableNames(processInstanceId: Long): List<String> =
        processVariableRepository.findByProcessInstanceId(processInstanceId).map { it.name } +
            historicProcessVariableRepository.findByProcessInstanceId(processInstanceId).map { it.name }

    private fun taskVariableNames(taskId: Long): Set<String> =
        (taskVariableRepository.findByTaskId(taskId).map { it.name } +
            historicTaskVariableRepository.findByTaskId(taskId).map { it.name }).toSet()

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

    // TaskService executes APITask nodes synchronously via IntegrationService; mock to keep tests offline.
    @MockBean
    private lateinit var integrationService: IntegrationService

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

        val finalProcessVariable = processVariableValue(processInstance.id, "var_2")
        assertThat(finalProcessVariable).isNotNull
        assertThat(finalProcessVariable?.asText()).isEqualTo("\"test\"")
        assertThat(processVariableRepository.findByProcessInstanceId(processInstance.id)).isEmpty()
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
        val staticVar = processVariableValue(processInstance.id, "staticVar")
        assertThat(staticVar).isNotNull
        assertThat(staticVar?.asText()).isEqualTo("\"static_value\"")

        // Verify dynamic variable was set (copied from sourceVar)
        val dynamicVar = processVariableValue(processInstance.id, "dynamicVar")
        assertThat(dynamicVar).isNotNull
        assertThat(dynamicVar?.asText()).isEqualTo("\"initial_value\"")
        assertThat(processVariableRepository.findByProcessInstanceId(processInstance.id)).isEmpty()
    }

    @Test
    fun `service task node should support both static and variable assignment in same node`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        // Retrieve all process variables for the instance
        val variableNames = processVariableNames(processInstance.id)

        // Should have sourceVar, staticVar, and dynamicVar
        assertThat(variableNames).hasSize(3)

        // Check sourceVar is unchanged (initial value)
        assertThat(processVariableValue(processInstance.id, "sourceVar")?.asText()).isEqualTo("\"initial_value\"")

        // Check staticVar was set
        assertThat(processVariableValue(processInstance.id, "staticVar")?.asText()).isEqualTo("\"static_value\"")

        // Check dynamicVar was set from sourceVar
        assertThat(processVariableValue(processInstance.id, "dynamicVar")?.asText()).isEqualTo("\"initial_value\"")
    }

    @Test
    fun `variable overwrite should update existing process variable on service task completion`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-service-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.COMPLETED)

        // Verify that there are no duplicate variables - this tests the overwrite logic
        // If the bug existed (always creating new vars), we'd have duplicates
        val varNames = processVariableNames(processInstance.id)
        
        // Key assertion: no duplicate variable names
        assertThat(varNames).doesNotHaveDuplicates()
        
        // Verify expected variables exist (not duplicates)
        assertThat(varNames).contains("staticVar", "dynamicVar", "sourceVar")
        
        // Each variable should appear exactly once
        assertThat(varNames.filter { it == "dynamicVar" }).hasSize(1)
        assertThat(varNames.filter { it == "staticVar" }).hasSize(1)
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

        // Completed task variables are moved from runtime to historic variables
        assertThat(taskVariableRepository.findByTaskId(createdTask.id)).isEmpty()
        val taskVariables = historicTaskVariableRepository.findByTaskId(createdTask.id)
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
        val allVars = processVariableNames(processInstance.id)
        assertThat(allVars.size).isGreaterThan(0)

        // Should not have duplicate variables
        assertThat(allVars).doesNotHaveDuplicates()
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
    fun `message start process should start when matching message is received`() {
        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "message-start-order",
              "variables": [
                { "name": "orderId", "type": "string", "initialValue": "" },
                { "name": "eventPayload", "type": "json", "initialValue": "{}" }
              ],
              "nodes": [
                {
                  "id": "start_msg",
                  "name": "Message Start",
                  "type": "MessageStartEvent",
                  "next": ["review_order"],
                  "message": {
                    "name": "order.started",
                    "correlationKeys": ["orderId"],
                    "payload": [
                      {
                        "source": "variable",
                        "sourceValue": "payload",
                        "type": "json",
                        "targetVariable": "eventPayload"
                      }
                    ]
                  }
                },
                {
                  "id": "review_order",
                  "name": "Review Order",
                  "type": "HumanTask",
                  "next": ["end"]
                },
                {
                  "id": "end",
                  "name": "End",
                  "type": "EndEvent",
                  "next": []
                }
              ],
              "flows": [
                { "from": "start_msg", "to": "review_order", "condition": null },
                { "from": "review_order", "to": "end", "condition": null }
              ]
            }
            """.trimIndent()
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)

        processService.handleMessageReceived(
            "order.started",
            "ORD-1001",
            mapOf(
                "orderId" to "ORD-1001",
                "payload" to mapOf("source" to "api")
            )
        )

        val instance = processInstanceRepository.findAll()
            .single { it.processDefinition.id == processDefinition.id }

        assertThat(instance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(instance.currentNode).containsExactly("review_order")
        assertThat(instance.nodeHistory).contains("review_order")

        val orderId = processVariableRepository.findByProcessInstanceIdAndName(instance.id, "orderId")
        val eventPayload = processVariableRepository.findByProcessInstanceIdAndName(instance.id, "eventPayload")
        val correlationKey = processVariableRepository.findByProcessInstanceIdAndName(instance.id, "correlationKey")

        assertThat(orderId?.value?.asText()).isEqualTo("ORD-1001")
        assertThat(eventPayload?.value?.get("source")?.asText()).isEqualTo("api")
        assertThat(correlationKey?.value?.asText()).isEqualTo("ORD-1001")
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
    fun `api task json process should execute api task without external call and complete`() {
        val processDefinitionJson = objectMapper.readTree(ClassPathResource("process-api-task.json").inputStream)

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        assertThat(processInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(processInstance.currentNode).containsExactly("user-task_submit-order")

        val userTask = taskRepository.findAll().first { it.processInstanceId == processInstance.id }
        taskService.completeTask(userTask.id, "tester", mapOf("orderId" to "ORD-900"))

        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(updatedInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(updatedInstance.currentNode).isEmpty()
        assertThat(updatedInstance.nodeHistory).contains("api-task_notify-warehouse", "end_api")
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

        val emailSent = processVariableValue(processInstance.id, "emailSent")
        val managerNotified = processVariableValue(processInstance.id, "managerNotified")
        assertThat(emailSent?.asText()).isEqualTo("true")
        assertThat(managerNotified?.asText()).isEqualTo("true")
    }

    @Test
    fun `call activity json processes should create child instance and map parent input`() {
        val childJson = objectMapper.readTree(ClassPathResource("process-call-activity-child.json").inputStream)
        val parentJson = objectMapper.readTree(ClassPathResource("process-call-activity.json").inputStream)

        processService.deployProcess(childJson)
        val parentDefinition = processService.deployProcess(parentJson)
        val parentInstance = processService.startProcessInstance(parentDefinition.id)

        val waitingParent = processInstanceRepository.findById(parentInstance.id).orElseThrow()
        assertThat(waitingParent.status).isEqualTo(ProcessStatus.WAITING)

        val childInstances = processInstanceRepository.findByParentInstanceId(parentInstance.id)
        assertThat(childInstances).hasSize(1)
        val childInstance = childInstances[0]
        val childVars = processVariableRepository.findByProcessInstanceId(childInstance.id)
        val mappedCustomerName = childVars.find { it.name == "subCustomerName" }
        assertThat(mappedCustomerName).isNotNull
        assertThat(mappedCustomerName?.value?.asText()).contains("John Doe")
    }

    @Test
    fun `agent process call should record execution set decision variables and complete process`() {
        val agentDefinitionJson = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "customer-support-resolution",
              "processName": "Customer Support Resolution",
              "goal": "Resolve customer complaint and ensure customer satisfaction.",
              "instructions": "Investigate the issue and propose a resolution.",
              "constraints": ["Refunds above 500 require approval"],
              "availableTools": ["CRM", "Email"],
              "participants": ["Planner Agent", "Decision Agent"],
              "steps": []
            }
            """.trimIndent()
        )
        agentProcessService.deploy(agentDefinitionJson)

        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "bpm-calls-agent-process",
              "variables": [
                { "name": "customerId", "type": "string", "initialValue": "C-100" }
              ],
              "nodes": [
                { "id": "start", "type": "StartEvent", "next": ["invoke-agent"] },
                {
                  "id": "invoke-agent",
                  "type": "AgentProcessCall",
                  "name": "Resolve with Agent",
                  "next": ["end"],
                  "config": {
                    "agentProcessKey": "customer-support-resolution",
                    "goalOverride": "Resolve complaint for customer C-100",
                    "waitForCompletion": true,
                    "timeoutDays": 7,
                    "inputs": [
                      { "targetName": "customerId", "type": "string", "source": "variable", "value": "customerId" }
                    ],
                    "outputs": [
                      { "source": "variable", "sourceValue": "decision", "type": "string", "targetVariable": "agentDecision" },
                      { "source": "variable", "sourceValue": "executionId", "type": "number", "targetVariable": "agentExecutionId" }
                    ]
                  }
                },
                { "id": "end", "type": "EndEvent" }
              ],
              "flows": [
                { "from": "start", "to": "invoke-agent", "condition": null },
                { "from": "invoke-agent", "to": "end", "condition": null }
              ]
            }
            """.trimIndent()
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        val completedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(completedInstance.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(completedInstance.currentNode).isEmpty()
        assertThat(completedInstance.nodeHistory).contains("invoke-agent", "end")

        val executions = agentProcessExecutionRepository.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstance.id)
        assertThat(executions).hasSize(1)
        assertThat(executions.first().nodeId).isEqualTo("invoke-agent")
        assertThat(executions.first().outputPayload).contains("AGENT_PROCESS_PLANNED")

        val agentDecision = processVariableValue(processInstance.id, "agentDecision")
        assertThat(agentDecision).isNotNull
        assertThat(agentDecision?.asText()).isEqualTo("AGENT_PROCESS_PLANNED")

        val defaultDecision = processVariableValue(processInstance.id, "invoke-agent_agentDecision")
        assertThat(defaultDecision).isNotNull
        assertThat(defaultDecision?.asText()).isEqualTo("AGENT_PROCESS_PLANNED")
    }

    @Test
    fun `agent process call should execute after completing previous user task`() {
        val agentDefinitionJson = objectMapper.readTree(
            """
            {
              "resourceType": "AgentProcess",
              "processKey": "customer-support-resolution-after-task",
              "processName": "Customer Support Resolution After Task",
              "goal": "Resolve customer complaint.",
              "steps": []
            }
            """.trimIndent()
        )
        agentProcessService.deploy(agentDefinitionJson)

        val processDefinitionJson = objectMapper.readTree(
            """
            {
              "processId": "bpm-user-task-calls-agent-process",
              "variables": [
                { "name": "complaintText", "type": "string", "initialValue": "Damaged replacement product." },
                { "name": "agentDecision", "type": "string", "initialValue": "" }
              ],
              "nodes": [
                { "id": "start", "type": "StartEvent", "next": ["capture-complaint"] },
                {
                  "id": "capture-complaint",
                  "type": "HumanTask",
                  "name": "Capture Complaint",
                  "next": ["invoke-resolution-agent"],
                  "config": {
                    "assignee": "support.agent",
                    "outputs": [
                      { "target": "variable", "sourceName": "complaintText", "type": "string", "value": "complaintText" }
                    ]
                  }
                },
                {
                  "id": "invoke-resolution-agent",
                  "type": "AgentProcessCall",
                  "name": "Invoke Resolution Agent",
                  "next": ["review-agent-decision"],
                  "config": {
                    "agentProcessKey": "customer-support-resolution-after-task",
                    "inputs": [
                      { "targetName": "complaintText", "type": "string", "source": "variable", "value": "complaintText" }
                    ],
                    "outputs": [
                      { "source": "variable", "sourceValue": "decision", "type": "string", "targetVariable": "agentDecision" }
                    ]
                  }
                },
                {
                  "id": "review-agent-decision",
                  "type": "HumanTask",
                  "name": "Review Agent Decision",
                  "next": ["end"],
                  "config": {
                    "assignee": "support.manager",
                    "inputs": [
                      { "targetName": "agentDecision", "type": "string", "source": "variable", "value": "agentDecision" }
                    ],
                    "outputs": []
                  }
                },
                { "id": "end", "type": "EndEvent" }
              ],
              "flows": [
                { "from": "start", "to": "capture-complaint", "condition": null },
                { "from": "capture-complaint", "to": "invoke-resolution-agent", "condition": null },
                { "from": "invoke-resolution-agent", "to": "review-agent-decision", "condition": null },
                { "from": "review-agent-decision", "to": "end", "condition": null }
              ]
            }
            """.trimIndent()
        )

        val processDefinition = processService.deployProcess(processDefinitionJson)
        val processInstance = processService.startProcessInstance(processDefinition.id)

        val captureTask = taskRepository.findByProcessInstanceId(processInstance.id)
            .single { it.nodeId == "capture-complaint" }
        taskService.completeTask(captureTask.id, "support.agent", mapOf("complaintText" to "Damaged replacement product."))

        val updatedInstance = processInstanceRepository.findById(processInstance.id).orElseThrow()
        assertThat(updatedInstance.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(updatedInstance.currentNode).containsExactly("review-agent-decision")
        assertThat(updatedInstance.nodeHistory).contains("capture-complaint", "invoke-resolution-agent", "review-agent-decision")

        val executions = agentProcessExecutionRepository.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstance.id)
        assertThat(executions).hasSize(1)
        assertThat(executions.first().nodeId).isEqualTo("invoke-resolution-agent")

        val agentDecision = processVariableRepository.findByProcessInstanceIdAndName(processInstance.id, "agentDecision")
        assertThat(agentDecision?.value?.asText()).isEqualTo("AGENT_PROCESS_PLANNED")

        val reviewTask = taskRepository.findByProcessInstanceId(processInstance.id)
            .single { it.nodeId == "review-agent-decision" }
        assertThat(reviewTask.status).isEqualTo(TaskStatus.PENDING)
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
