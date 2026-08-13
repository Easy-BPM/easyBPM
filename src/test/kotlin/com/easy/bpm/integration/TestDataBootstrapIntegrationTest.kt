package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.easy.bpm.repository.codetask.CodeClassMetadataRepository
import com.easy.bpm.repository.codetask.CodeTaskJarRepository
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(properties = ["easybpm.test-data.enabled=true"])
class TestDataBootstrapIntegrationTest(
    @Autowired private val processDefinitionRepository: ProcessDefinitionRepository,
    @Autowired private val formDefinitionRepository: FormDefinitionRepository,
    @Autowired private val agentProcessDefinitionRepository: AgentProcessDefinitionRepository,
    @Autowired private val codeTaskJarRepository: CodeTaskJarRepository,
    @Autowired private val codeClassMetadataRepository: CodeClassMetadataRepository,
    @Autowired private val processInstanceRepository: ProcessInstanceRepository,
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val processVariableRepository: ProcessVariableRepository,
    @Autowired private val historicProcessVariableRepository: HistoricProcessVariableRepository
) : IntegrationTestBase() {

    @MockBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Test
    fun `enabled test data flag should seed process definitions instances tasks and variables`() {
        val activeDefinition = processDefinitionRepository
            .findTopByKeyOrderByVersionDesc("easybpm-test-supplier-onboarding")
        val completedDefinition = processDefinitionRepository
            .findTopByKeyOrderByVersionDesc("easybpm-test-expense-approval")

        assertThat(activeDefinition).isNotNull
        assertThat(completedDefinition).isNotNull

        val activeInstances = processInstanceRepository.findByProcessDefinitionId(activeDefinition!!.id)
        val completedInstances = processInstanceRepository.findByProcessDefinitionId(completedDefinition!!.id)

        assertThat(activeInstances).hasSize(1)
        assertThat(completedInstances).hasSize(1)
        assertThat(activeInstances.first().status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(completedInstances.first().status).isEqualTo(ProcessStatus.COMPLETED)

        val pendingTasks = taskRepository.findByProcessInstanceId(activeInstances.first().id)
        val completedTasks = taskRepository.findByProcessInstanceId(completedInstances.first().id)

        assertThat(pendingTasks).anySatisfy { task ->
            assertThat(task.nodeId).isEqualTo("review-supplier")
            assertThat(task.status).isEqualTo(TaskStatus.PENDING)
        }
        assertThat(completedTasks).anySatisfy { task ->
            assertThat(task.nodeId).isEqualTo("approve-expense")
            assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
            assertThat(task.assignee).isEqualTo("admin")
        }

        val activeVariables = processVariableRepository.findByProcessInstanceId(activeInstances.first().id)
        val completedRuntimeVariables = processVariableRepository.findByProcessInstanceId(completedInstances.first().id)
        val completedVariables = historicProcessVariableRepository.findByProcessInstanceId(completedInstances.first().id)

        assertThat(activeVariables.map { it.name })
            .contains("supplierName", "requester", "riskScore", "contractValue")
        assertThat(completedRuntimeVariables).isEmpty()
        assertThat(completedVariables.map { it.name })
            .contains("requester", "amount", "costCenter", "decision", "approvedBy", "approvalComment")
    }

    @Test
    fun `enabled test data flag should deploy modeler qa forms agents and processes`() {
        assertThat(formDefinitionRepository.findTopByFormIdOrderByVersionDesc("qaIntakeForm")).isNotNull
        assertThat(formDefinitionRepository.findTopByFormIdOrderByVersionDesc("qaManagerReview")).isNotNull
        assertThat(formDefinitionRepository.findTopByFormIdOrderByVersionDesc("qaFinanceReview")).isNotNull

        assertThat(agentProcessDefinitionRepository.findTopByKeyOrderByVersionDesc("customer-support-resolution")).isNotNull

        val testServiceJar = codeTaskJarRepository.findAll().singleOrNull { it.fileName == "test-service.jar" }
        assertThat(testServiceJar).isNotNull
        assertThat(codeClassMetadataRepository.findByJarId(testServiceJar!!.id!!).map { it.methodName })
            .contains("greet", "add", "multiply", "processOrder", "validateEmail")

        assertThat(processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_user_task_forms")).isNotNull
        assertThat(processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_gateway_routing")).isNotNull
        assertThat(processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_api_components")).isNotNull
        assertThat(processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_message_timer_events")).isNotNull
        val codeTaskProcess = processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_code_task_component")
        assertThat(codeTaskProcess).isNotNull
        assertThat(codeTaskProcess!!.definitionJson).contains("\"jarId\": ${testServiceJar.id}")
        assertThat(processDefinitionRepository.findTopByKeyOrderByVersionDesc("qa_agent_process_call")).isNotNull
    }
}
