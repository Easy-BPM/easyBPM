package com.easy.bpm.config

import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class TestDataBootstrap(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val taskRepository: TaskRepository,
    private val processService: ProcessService,
    private val taskService: TaskService,
    private val objectMapper: ObjectMapper,
    @Value("\${easybpm.test-data.enabled:false}") private val enabled: Boolean
) : ApplicationRunner {

    companion object {
        private val logger = LoggerFactory.getLogger(TestDataBootstrap::class.java)
        private const val ACTIVE_APPROVAL_KEY = "easybpm-test-supplier-onboarding"
        private const val COMPLETED_APPROVAL_KEY = "easybpm-test-expense-approval"
    }

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!enabled) {
            return
        }

        if (processDefinitionRepository.findTopByKeyOrderByVersionDesc(ACTIVE_APPROVAL_KEY) != null ||
            processDefinitionRepository.findTopByKeyOrderByVersionDesc(COMPLETED_APPROVAL_KEY) != null
        ) {
            logger.info("Easy BPM test data already exists; skipping bootstrap.")
            return
        }

        logger.info("Bootstrapping Easy BPM test process data.")

        val activeDefinition = processService.deployProcess(objectMapper.readTree(activeSupplierOnboardingProcess()))
        processService.startProcessInstance(
            activeDefinition.id,
            mapOf(
                "supplierName" to "Acme Supplies",
                "requester" to "maria.ops",
                "riskScore" to 72,
                "contractValue" to 18500.00
            )
        )

        val completedDefinition = processService.deployProcess(objectMapper.readTree(completedExpenseApprovalProcess()))
        val completedInstance = processService.startProcessInstance(
            completedDefinition.id,
            mapOf(
                "requester" to "john.finance",
                "amount" to 1280.50,
                "costCenter" to "FIN-OPS"
            )
        )

        val pendingTask = taskRepository.findByProcessInstanceId(completedInstance.id)
            .firstOrNull { it.status == TaskStatus.PENDING }

        if (pendingTask != null) {
            taskService.completeTask(
                pendingTask.id,
                "admin",
                mapOf(
                    "decision" to "APPROVED",
                    "approvedBy" to "admin",
                    "approvalComment" to "Seeded approval for demo data."
                )
            )
        }

        logger.info("Easy BPM test process data bootstrap completed.")
    }

    private fun activeSupplierOnboardingProcess() =
        """
        {
          "processId": "$ACTIVE_APPROVAL_KEY",
          "processName": "Test Supplier Onboarding",
          "description": "Seeded process used to test active instances, tasks and variables.",
          "nodes": [
            { "id": "start", "name": "Start", "type": "StartEvent", "next": ["review-supplier"] },
            {
              "id": "review-supplier",
              "name": "Review supplier onboarding",
              "type": "HumanTask",
              "next": ["end"],
              "config": {
                "inputs": [
                  { "targetName": "supplierName", "type": "string", "source": "variable", "value": "supplierName" },
                  { "targetName": "riskScore", "type": "number", "source": "variable", "value": "riskScore" },
                  { "targetName": "contractValue", "type": "number", "source": "variable", "value": "contractValue" }
                ],
                "outputs": [
                  { "sourceName": "approved", "type": "boolean", "target": "variable", "value": "supplierApproved" }
                ]
              }
            },
            { "id": "end", "name": "End", "type": "EndEvent", "next": [] }
          ],
          "flows": [
            { "from": "start", "to": "review-supplier", "condition": null },
            { "from": "review-supplier", "to": "end", "condition": null }
          ]
        }
        """.trimIndent()

    private fun completedExpenseApprovalProcess() =
        """
        {
          "processId": "$COMPLETED_APPROVAL_KEY",
          "processName": "Test Expense Approval",
          "description": "Seeded process used to test completed instances, completed tasks and final variables.",
          "nodes": [
            { "id": "start", "name": "Start", "type": "StartEvent", "next": ["approve-expense"] },
            {
              "id": "approve-expense",
              "name": "Approve expense",
              "type": "HumanTask",
              "next": ["end"],
              "config": {
                "inputs": [
                  { "targetName": "requester", "type": "string", "source": "variable", "value": "requester" },
                  { "targetName": "amount", "type": "number", "source": "variable", "value": "amount" },
                  { "targetName": "costCenter", "type": "string", "source": "variable", "value": "costCenter" }
                ],
                "outputs": [
                  { "sourceName": "decision", "type": "string", "target": "variable", "value": "decision" },
                  { "sourceName": "approvedBy", "type": "string", "target": "variable", "value": "approvedBy" },
                  { "sourceName": "approvalComment", "type": "string", "target": "variable", "value": "approvalComment" }
                ]
              }
            },
            { "id": "end", "name": "End", "type": "EndEvent", "next": [] }
          ],
          "flows": [
            { "from": "start", "to": "approve-expense", "condition": null },
            { "from": "approve-expense", "to": "end", "condition": null }
          ]
        }
        """.trimIndent()
}
