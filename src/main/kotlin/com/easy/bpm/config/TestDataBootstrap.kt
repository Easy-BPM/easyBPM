package com.easy.bpm.config

import com.easy.bpm.entity.CodeTaskJar
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.repository.agent.AgentProcessDefinitionRepository
import com.easy.bpm.repository.codetask.CodeClassMetadataRepository
import com.easy.bpm.repository.codetask.CodeTaskJarRepository
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.service.agent.AgentProcessService
import com.easy.bpm.service.code.CodeClassDiscoveryService
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TestDataBootstrap(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val formDefinitionRepository: FormDefinitionRepository,
    private val agentProcessDefinitionRepository: AgentProcessDefinitionRepository,
    private val codeTaskJarRepository: CodeTaskJarRepository,
    private val codeClassMetadataRepository: CodeClassMetadataRepository,
    private val taskRepository: TaskRepository,
    private val processService: ProcessService,
    private val formService: FormService,
    private val agentProcessService: AgentProcessService,
    private val codeClassDiscoveryService: CodeClassDiscoveryService,
    private val taskService: TaskService,
    private val objectMapper: ObjectMapper,
    @Value("\${easybpm.test-data.enabled:false}") private val enabled: Boolean,
    @Value("\${easybpm.test-data.qa-resource-path:modeler-qa/qa-processes}") private val qaResourcePath: String
) : ApplicationRunner {

    companion object {
        private val logger = LoggerFactory.getLogger(TestDataBootstrap::class.java)
        private const val ACTIVE_APPROVAL_KEY = "easybpm-test-supplier-onboarding"
        private const val COMPLETED_APPROVAL_KEY = "easybpm-test-expense-approval"
        private const val QA_CODE_TASK_PROCESS_ID = "qa_code_task_component"
    }

    private val qaJarIds = mutableMapOf<String, Long>()

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!enabled) {
            return
        }

        logger.info("Bootstrapping Easy BPM test process data.")

        deployLegacyDemoData()
        deployModelerQaLibrary()

        logger.info("Easy BPM test process data bootstrap completed.")
    }

    private fun deployLegacyDemoData() {
        if (processDefinitionRepository.findTopByKeyOrderByVersionDesc(ACTIVE_APPROVAL_KEY) == null) {
            deployActiveSupplierOnboardingDemo()
        } else {
            logger.info("Easy BPM test data process '{}' already deployed; skipping.", ACTIVE_APPROVAL_KEY)
        }

        if (processDefinitionRepository.findTopByKeyOrderByVersionDesc(COMPLETED_APPROVAL_KEY) == null) {
            deployCompletedExpenseApprovalDemo()
        } else {
            logger.info("Easy BPM test data process '{}' already deployed; skipping.", COMPLETED_APPROVAL_KEY)
        }
    }

    private fun deployActiveSupplierOnboardingDemo() {
        val activeDefinition = processService.deployProcess(objectMapper.readTree(activeSupplierOnboardingProcess()))
        logger.info(
            "Easy BPM test data deployed process '{}' version {} from legacy demo.",
            activeDefinition.key,
            activeDefinition.version
        )
        processService.startProcessInstance(
            activeDefinition.id,
            mapOf(
                "supplierName" to "Acme Supplies",
                "requester" to "maria.ops",
                "riskScore" to 72,
                "contractValue" to 18500.00
            )
        )
        logger.info("Easy BPM test data started active demo instance for process '{}'.", activeDefinition.key)
    }

    private fun deployCompletedExpenseApprovalDemo() {
        val completedDefinition = processService.deployProcess(objectMapper.readTree(completedExpenseApprovalProcess()))
        logger.info(
            "Easy BPM test data deployed process '{}' version {} from legacy demo.",
            completedDefinition.key,
            completedDefinition.version
        )
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
            logger.info("Easy BPM test data completed seeded task '{}' for process '{}'.", pendingTask.id, completedDefinition.key)
        }
    }

    private fun deployModelerQaLibrary() {
        val manifest = readQaJson("manifest.json") ?: run {
            logger.warn(
                "Easy BPM test data QA manifest not found at classpath '{}'; skipping modeler QA deployments.",
                "$qaResourcePath/manifest.json"
            )
            return
        }

        logger.info(
            "Easy BPM test data deploying QA library '{}' version {} from '{}'.",
            manifest.path("library").asText("unknown"),
            manifest.path("version").asText("unknown"),
            qaResourcePath
        )

        manifest.path("formsBackend").forEach { entry -> deployQaForm(entry) }
        manifest.path("agentProcesses").forEach { entry -> deployQaAgentProcess(entry) }
        manifest.path("jars").forEach { entry -> deployQaJar(entry) }
        manifest.path("processes").forEach { entry -> deployQaProcess(entry) }
    }

    private fun deployQaForm(entry: JsonNode) {
        val formId = entry.path("id").asText()
        val file = entry.path("file").asText()
        if (formId.isBlank() || file.isBlank()) {
            logger.warn("Easy BPM test data skipped QA form manifest entry with missing id or file: {}", entry)
            return
        }
        if (formDefinitionRepository.findTopByFormIdOrderByVersionDesc(formId) != null) {
            logger.info("Easy BPM test data QA form '{}' already deployed; skipping.", formId)
            return
        }

        val json = readQaJson(file) ?: run {
            logger.warn("Easy BPM test data skipped QA form '{}' because '{}' was not found.", formId, file)
            return
        }
        val deployed = formService.deploy(
            json.path("formId").asText(formId),
            json.path("name").asText(formId),
            json.path("schema")
        )
        logger.info(
            "Easy BPM test data deployed QA form '{}' version {} from '{}'.",
            deployed.formId,
            deployed.version,
            file
        )
    }

    private fun deployQaAgentProcess(entry: JsonNode) {
        val agentId = entry.path("id").asText()
        val file = entry.path("file").asText()
        if (agentId.isBlank() || file.isBlank()) {
            logger.warn("Easy BPM test data skipped QA agent process manifest entry with missing id or file: {}", entry)
            return
        }
        if (agentProcessDefinitionRepository.findTopByKeyOrderByVersionDesc(agentId) != null) {
            logger.info("Easy BPM test data QA agent process '{}' already deployed; skipping.", agentId)
            return
        }

        val json = readQaJson(file) ?: run {
            logger.warn("Easy BPM test data skipped QA agent process '{}' because '{}' was not found.", agentId, file)
            return
        }
        val deployed = agentProcessService.deploy(json)
        logger.info(
            "Easy BPM test data deployed QA agent process '{}' version {} from '{}'.",
            deployed.key,
            deployed.version,
            file
        )
    }

    private fun deployQaJar(entry: JsonNode) {
        val jarId = entry.path("id").asText()
        val file = entry.path("file").asText()
        if (jarId.isBlank() || file.isBlank()) {
            logger.warn("Easy BPM test data skipped QA JAR manifest entry with missing id or file: {}", entry)
            return
        }

        val resource = readQaResource(file) ?: run {
            logger.warn("Easy BPM test data skipped QA JAR '{}' because '{}' was not found.", jarId, file)
            return
        }
        val jarBytes = resource.inputStream.use { it.readBytes() }
        if (!codeClassDiscoveryService.isValidJar(jarBytes)) {
            logger.warn("Easy BPM test data skipped QA JAR '{}' because '{}' is not a valid JAR.", jarId, file)
            return
        }

        val fileHash = codeClassDiscoveryService.hashJar(jarBytes)
        val existing = codeTaskJarRepository.findByFileHash(fileHash)
        if (existing != null) {
            qaJarIds[jarId] = existing.id!!
            logger.info("Easy BPM test data QA JAR '{}' already deployed as database id {}; skipping.", jarId, existing.id)
            return
        }

        val savedJar = codeTaskJarRepository.save(
            CodeTaskJar(
                content = jarBytes,
                fileName = file.substringAfterLast('/'),
                fileHash = fileHash,
                uploadDate = LocalDateTime.now(),
                uploadedBy = "test-data-bootstrap",
                description = "Seeded by EASY_BPM_TEST_DATA from $file"
            )
        )
        val methodCount = discoverAndStoreJarMetadata(savedJar, jarBytes)
        qaJarIds[jarId] = savedJar.id!!
        logger.info(
            "Easy BPM test data deployed QA JAR '{}' as database id {} with {} discovered methods from '{}'.",
            jarId,
            savedJar.id,
            methodCount,
            file
        )
    }

    private fun deployQaProcess(entry: JsonNode) {
        val processId = entry.path("id").asText()
        val file = entry.path("file").asText()
        if (processId.isBlank() || file.isBlank()) {
            logger.warn("Easy BPM test data skipped QA process manifest entry with missing id or file: {}", entry)
            return
        }
        if (processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId) != null) {
            logger.info("Easy BPM test data QA process '{}' already deployed; skipping.", processId)
            return
        }

        val resource = readQaResource(file) ?: run {
            logger.warn("Easy BPM test data skipped QA process '{}' because '{}' was not found.", processId, file)
            return
        }

        try {
            val deployed = if (file.endsWith(".bpmn") || file.endsWith(".xml")) {
                processService.deployProcess(prepareQaProcessXml(processId, resource.inputStream.use { it.bufferedReader().readText() }))
            } else {
                processService.deployProcess(prepareQaProcessJson(processId, resource.inputStream.use { objectMapper.readTree(it) }))
            }
            logger.info(
                "Easy BPM test data deployed QA process '{}' version {} from '{}'.",
                deployed.key,
                deployed.version,
                file
            )
        } catch (ex: RuntimeException) {
            logger.warn(
                "Easy BPM test data could not deploy QA process '{}' from '{}': {}",
                processId,
                file,
                ex.message
            )
        }
    }

    private fun discoverAndStoreJarMetadata(jar: CodeTaskJar, jarBytes: ByteArray): Int {
        val classLoader = codeClassDiscoveryService.createClassLoader(jarBytes)
        return classLoader.use { loader ->
            codeClassDiscoveryService.discoverClasses(loader).sumOf { className ->
                try {
                    val clazz = loader.loadClass(className)
                    val methods = codeClassDiscoveryService.discoverMethods(clazz)
                    methods.forEach { method ->
                        codeClassMetadataRepository.save(
                            codeClassDiscoveryService.extractMethodMetadata(jar.id!!, className, method)
                        )
                    }
                    methods.size
                } catch (ex: Exception) {
                    logger.warn("Easy BPM test data could not inspect QA JAR class '{}': {}", className, ex.message)
                    0
                }
            }
        }
    }

    private fun prepareQaProcessJson(processId: String, json: JsonNode): JsonNode {
        if (processId != QA_CODE_TASK_PROCESS_ID) {
            return json
        }

        val testServiceJarId = qaJarIds["test-service"] ?: return json
        val copy = json.deepCopy<ObjectNode>()
        replaceJarIds(copy, testServiceJarId)
        logger.info(
            "Easy BPM test data bound QA process '{}' to QA JAR database id {}.",
            processId,
            testServiceJarId
        )
        return copy
    }

    private fun prepareQaProcessXml(processId: String, xml: String): String {
        if (processId != QA_CODE_TASK_PROCESS_ID) {
            return xml
        }

        val testServiceJarId = qaJarIds["test-service"] ?: return xml
        logger.info(
            "Easy BPM test data bound QA process '{}' to QA JAR database id {}.",
            processId,
            testServiceJarId
        )
        return Regex(""""jarId"\s*:\s*\d+""").replace(xml, """"jarId":$testServiceJarId""")
    }

    private fun replaceJarIds(node: JsonNode, jarId: Long) {
        when (node) {
            is ObjectNode -> {
                if (node.has("jarId")) {
                    node.put("jarId", jarId)
                }
                node.properties().forEach { (_, child) -> replaceJarIds(child, jarId) }
            }
            is ArrayNode -> node.forEach { child -> replaceJarIds(child, jarId) }
        }
    }

    private fun readQaJson(relativePath: String): JsonNode? {
        val resource = readQaResource(relativePath) ?: return null
        return resource.inputStream.use { objectMapper.readTree(it) }
    }

    private fun readQaResource(relativePath: String): ClassPathResource? {
        val resourcePath = "${qaResourcePath.trim('/')}/$relativePath"
        val resource = ClassPathResource(resourcePath)
        if (!resource.exists()) {
            return null
        }
        return resource
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
