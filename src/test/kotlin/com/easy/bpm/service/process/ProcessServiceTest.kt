package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.handler.AITaskHandler
import com.easy.bpm.handler.AgentProcessCallHandler
import com.easy.bpm.handler.CodeTaskHandler
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.repository.worker.WorkerRequestRepository
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.incident.IncidentService
import com.easy.bpm.service.message.MessageSubscriptionService
import com.easy.bpm.service.metrics.MetricsService
import com.easy.bpm.service.process.handler.*
import com.easy.bpm.service.variable.HistoricVariableArchiver
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.util.*

class ProcessServiceTest : FunSpec() {
    init {
    val mockProcessDefinitionRepository = mockk<ProcessDefinitionRepository>()
    val mockProcessInstanceRepository = mockk<ProcessInstanceRepository>()
    val mockProcessVariableRepository = mockk<ProcessVariableRepository>()
    val mockTaskVariableRepository = mockk<TaskVariableRepository>()
    val mockFormService = mockk<FormService>()
    val mockTaskRepository = mockk<TaskRepository>()
    val mockObjectMapper = spyk(ObjectMapper())
    val mockRabbitPublisher = mockk<com.easy.bpm.messaging.RabbitPublisher>(relaxed = true)
    val mockGatewayService = mockk<GatewayService>()
    val mockMessageSubscriptionService = mockk<MessageSubscriptionService>()
    val mockMetricsService = mockk<MetricsService>(relaxed = true)
    val mockWorkerRequestRepository = mockk<WorkerRequestRepository>()
    val mockCallActivityHandler = mockk<CallActivityHandler>()
    val mockCallActivityMappingRepository = mockk<CallActivityMappingRepository>()
    val mockAITaskHandler = mockk<AITaskHandler>()
    val mockAgentProcessCallHandler = mockk<AgentProcessCallHandler>()
    val mockCodeTaskHandler = mockk<CodeTaskHandler>()
    val mockIncidentService = mockk<IncidentService>(relaxed = true)
    val mockTimelineService = mockk<ProcessInstanceTimelineService>(relaxed = true)
    val mockHistoricVariableArchiver = mockk<HistoricVariableArchiver>(relaxed = true)
    val pageableSanitizer = ProcessPageableSanitizer()
    val variableManager = ProcessVariableManager(
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockTaskVariableRepository,
        mockObjectMapper
    )
    val failureHandler = ProcessFailureHandler(
        mockProcessInstanceRepository,
        mockIncidentService,
        mockTimelineService
    )
    val navigator = ProcessNavigator(
        mockGatewayService,
        mockProcessInstanceRepository,
        mockTimelineService,
        mockHistoricVariableArchiver
    )
    val messageNodeHandler = ProcessMessageNodeHandler(
        mockMessageSubscriptionService,
        mockRabbitPublisher,
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        variableManager,
        mockTimelineService
    )
    val serviceTaskOutputMapper = ServiceTaskOutputMapper(
        mockObjectMapper,
        variableManager
    )
    val userTaskHandler = ProcessUserTaskHandler(
        mockFormService,
        mockTaskRepository,
        mockRabbitPublisher,
        mockMetricsService,
        variableManager,
        mockTimelineService
    )
    val serviceTaskHandler = ProcessServiceTaskHandler(
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockRabbitPublisher,
        mockObjectMapper,
        variableManager,
        mockTimelineService
    )
    val workerCallbackHandler = ProcessWorkerCallbackHandler(
        mockProcessInstanceRepository,
        mockObjectMapper,
        navigator,
        serviceTaskOutputMapper,
        failureHandler,
        mockTimelineService
    )
    val messageReceivedHandler = ProcessMessageReceivedHandler(
        mockProcessInstanceRepository,
        mockMessageSubscriptionService,
        mockObjectMapper,
        variableManager,
        navigator,
        mockMetricsService,
        mockTimelineService
    )
    val messageStartResolver = ProcessMessageStartResolver(
        mockProcessDefinitionRepository,
        mockObjectMapper
    )
    val processAiTaskHandler = ProcessAiTaskHandler(
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockAITaskHandler,
        variableManager
    )
    val processAgentCallHandler = ProcessAgentCallHandler(
        mockAgentProcessCallHandler,
        navigator,
        mockTimelineService
    )
    val lifecycleManager: ProcessInstanceLifecycleManager = ProcessInstanceLifecycleManager(
        mockProcessInstanceRepository,
        mockProcessVariableRepository,
        mockTaskVariableRepository,
        mockTaskRepository,
        mockWorkerRequestRepository,
        mockObjectMapper,
        mockMessageSubscriptionService,
        mockMetricsService,
        mockTimelineService,
        userTaskHandler,
        mockHistoricVariableArchiver
    )
    val executionEngine: ProcessExecutionEngine = ProcessExecutionEngine(
        mockMetricsService,
        failureHandler,
        navigator,
        messageNodeHandler,
        variableManager,
        userTaskHandler,
        serviceTaskHandler,
        processAiTaskHandler,
        processAgentCallHandler,
        mockCodeTaskHandler,
        mockCallActivityHandler,
        mockTimelineService,
        lifecycleManager
    )
    val mockDeploymentService = mockk<ProcessDeploymentService>()
    val mockInstanceStarter = mockk<ProcessInstanceStarter>()
    val workerCallbackService: ProcessWorkerCallbackService = ProcessWorkerCallbackService(
        mockMetricsService,
        navigator,
        executionEngine,
        workerCallbackHandler
    )
    val messageRuntimeService: ProcessMessageRuntimeService = ProcessMessageRuntimeService(
        mockProcessInstanceRepository,
        mockObjectMapper,
        navigator,
        executionEngine,
        messageReceivedHandler,
        messageStartResolver,
        mockTimelineService,
        mockInstanceStarter
    )

    val processService: ProcessService = ProcessService(
        processDefinitionRepository = mockProcessDefinitionRepository,
        processInstanceRepository = mockProcessInstanceRepository,
        processVariableRepository = mockProcessVariableRepository,
        callActivityMappingRepository = mockCallActivityMappingRepository,
        pageableSanitizer = pageableSanitizer,
        variableManager = variableManager,
        lifecycleManager = lifecycleManager,
        deploymentService = mockDeploymentService,
        instanceStarter = mockInstanceStarter,
        workerCallbackService = workerCallbackService,
        messageRuntimeService = messageRuntimeService
    )

    val objectMapper = ObjectMapper()

    beforeEach {
        clearAllMocks()
    }

    context("deployProcess") {
        test("should deploy a new process definition successfully") {
            // Arrange
            val processXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="my-process" name="my-process" isExecutable="true"/>
                </bpmn:definitions>
            """.trimIndent()

            val expectedDefinition = ProcessDefinition(
                id = 1,
                processName = "my-process",
                definitionJson = processXml,
                version = 1
            )
            every { mockDeploymentService.deployProcess(processXml) } returns expectedDefinition

            // Act
            val result = processService.deployProcess(processXml)

            // Assert
            result shouldNotBe null
            result.processName shouldBe "my-process"
            result.version shouldBe 1
            verify { mockDeploymentService.deployProcess(processXml) }
        }

        test("should increment version for existing process definition") {
            // Arrange
            val processXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="my-process" name="my-process" isExecutable="true"/>
                </bpmn:definitions>
            """.trimIndent()

            val expectedDefinition = ProcessDefinition(
                id = 2,
                processName = "my-process",
                definitionJson = processXml,
                version = 2
            )
            every { mockDeploymentService.deployProcess(processXml) } returns expectedDefinition

            // Act
            val result = processService.deployProcess(processXml)

            // Assert
            result.version shouldBe 2
            verify { mockDeploymentService.deployProcess(processXml) }
        }

        test("should throw exception for invalid process definition") {
            // Arrange
            val invalidDefinition = """{"version":1}"""

            every { mockDeploymentService.deployProcess(invalidDefinition) } throws IllegalArgumentException("Process definitions must be deployed as BPMN XML")

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processService.deployProcess(invalidDefinition)
            }
        }
    }

    context("startProcessInstance") {
        test("should throw exception when process definition not found") {
            // Arrange
            val processDefinitionId = 999L
            every { mockProcessDefinitionRepository.findById(processDefinitionId) } returns Optional.empty()

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                processService.startProcessInstance(processDefinitionId)
            }
        }

        test("should create instance with correct status") {
            // Arrange - test basic instantiation without full execution engine
            val definitionId = 1L
            val definition = ProcessDefinition(
                id = definitionId,
                processName = "simple-process",
                definitionJson = unitTestBpmnXml("simple", "[]"),
                version = 1
            )

            every { mockProcessDefinitionRepository.findById(definitionId) } returns Optional.of(definition)
            val expectedInstance = ProcessInstance(
                id = 100,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )
            every {
                mockInstanceStarter.startWithDefinition(
                    definition = definition,
                    initialVariables = emptyMap(),
                    startNodeId = null
                )
            } returns expectedInstance

            // Act
            val result = processService.startProcessInstance(definitionId)

            // Assert
            result.status shouldBe ProcessStatus.ACTIVE
            verify {
                mockInstanceStarter.startWithDefinition(
                    definition = definition,
                    initialVariables = emptyMap(),
                    startNodeId = null
                )
            }
        }
    }

    context("getProcessInstances") {
        test("should retrieve paginated process instances") {
            // Arrange
            val instance1 = ProcessInstance(
                id = 1,
                processDefinition = ProcessDefinition(id = 1, processName = "proc-1", definitionJson = "{}", version = 1),
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )
            val instance2 = ProcessInstance(
                id = 2,
                processDefinition = ProcessDefinition(id = 2, processName = "proc-2", definitionJson = "{}", version = 1),
                status = ProcessStatus.ACTIVE,
                currentNode = emptyList()
            )

            val page: Page<ProcessInstance> = PageImpl(listOf(instance1, instance2), PageRequest.of(0, 10), 2)
            every { mockProcessInstanceRepository.findAll(any<Pageable>()) } returns page

            // Act
            val result = processService.getProcessInstances(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.totalElements shouldBe 2
            verify { mockProcessInstanceRepository.findAll(any<Pageable>()) }
        }

        test("should return empty page when no instances exist") {
            // Arrange
            val emptyPage: Page<ProcessInstance> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            every { mockProcessInstanceRepository.findAll(any<Pageable>()) } returns emptyPage

            // Act
            val result = processService.getProcessInstances(PageRequest.of(0, 10))

            // Assert
            result.content.shouldBeEmpty()
            result.totalElements shouldBe 0
        }

        test("should ignore unsupported sort fields for process instances") {
            // Arrange
            val page: Page<ProcessInstance> = PageImpl(emptyList(), PageRequest.of(0, 10), 0)
            val capturedPageable = slot<Pageable>()
            every { mockProcessInstanceRepository.findAll(capture(capturedPageable)) } returns page

            // Act
            processService.getProcessInstances(PageRequest.of(0, 10, Sort.by("string").ascending()))

            // Assert
            capturedPageable.captured.sort.getOrderFor("string") shouldBe null
            capturedPageable.captured.sort.getOrderFor("createdAt")?.direction shouldBe Sort.Direction.DESC
            capturedPageable.captured.sort.getOrderFor("id")?.direction shouldBe Sort.Direction.DESC
        }
    }

    context("getProcessInstanceById") {
        test("should return process instance when it exists") {
            // Arrange
            val instanceId = 100L
            val instance = ProcessInstance(
                id = instanceId,
                processDefinition = ProcessDefinition(id = 1, processName = "proc", definitionJson = "{}", version = 1),
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("node-1")
            )
            every { mockProcessInstanceRepository.findById(instanceId) } returns Optional.of(instance)

            // Act
            val result = processService.getProcessInstanceById(instanceId)

            // Assert
            result shouldBe instance
            result?.currentNode?.shouldContain("node-1")
        }

        test("should return null when process instance does not exist") {
            // Arrange
            val instanceId = 999L
            every { mockProcessInstanceRepository.findById(instanceId) } returns Optional.empty()

            // Act
            val result = processService.getProcessInstanceById(instanceId)

            // Assert
            result shouldBe null
        }
    }

    context("assignProcessVariables") {
        test("should reject variable assignment for completed process instance") {
            // Arrange
            val instanceId = 100L
            val instance = ProcessInstance(
                id = instanceId,
                processDefinition = ProcessDefinition(id = 1, processName = "proc", definitionJson = "{}", version = 1),
                status = ProcessStatus.COMPLETED,
                currentNode = emptyList()
            )
            every { mockProcessInstanceRepository.findById(instanceId) } returns Optional.of(instance)

            // Act & Assert
            shouldThrow<IllegalStateException> {
                processService.assignProcessVariables(instanceId, mapOf("approved" to true))
            }.message shouldBe "Cannot assign variables to a completed process instance"

            verify(exactly = 0) { mockProcessInstanceRepository.findByIdForUpdate(instanceId) }
            verify(exactly = 0) { mockProcessVariableRepository.save(any<ProcessVariable>()) }
        }
    }

    context("getLatestProcessDefinitions") {
        test("should retrieve latest versions of all processes") {
            // Arrange
            val definition1 = ProcessDefinition(id = 1, processName = "proc-1", definitionJson = "{}", version = 2)
            val definition2 = ProcessDefinition(id = 2, processName = "proc-2", definitionJson = "{}", version = 1)

            val page: Page<ProcessDefinition> = PageImpl(
                listOf(definition1, definition2),
                PageRequest.of(0, 10),
                2
            )
            every { mockProcessDefinitionRepository.findLatestVersionProcesses(any<Pageable>()) } returns page

            // Act
            val result = processService.getLatestProcessDefinitions(PageRequest.of(0, 10))

            // Assert
            result.content shouldHaveSize 2
            result.content[0].version shouldBe 2
            result.content[1].version shouldBe 1
        }

        test("should ignore unsupported sort fields and apply safe default sort") {
            // Arrange
            val definition = ProcessDefinition(id = 1, processName = "proc-1", definitionJson = "{}", version = 2)
            val page: Page<ProcessDefinition> = PageImpl(
                listOf(definition),
                PageRequest.of(0, 10),
                1
            )
            val capturedPageable = slot<Pageable>()
            every { mockProcessDefinitionRepository.findLatestVersionProcesses(capture(capturedPageable)) } returns page

            // Act
            processService.getLatestProcessDefinitions(PageRequest.of(0, 10, Sort.by("string").ascending()))

            // Assert
            capturedPageable.captured.sort.getOrderFor("string") shouldBe null
            capturedPageable.captured.sort.getOrderFor("key")?.direction shouldBe Sort.Direction.ASC
            capturedPageable.captured.sort.getOrderFor("version")?.direction shouldBe Sort.Direction.DESC
        }
    }

    context("moveProcessNode") {
        test("should remove pending task on source node and create pending task on target user task") {
            // Arrange
            val processInstanceId = 55L
            val definitionJson = unitTestBpmnXml(
                "approval",
                """[
                    {"id": "manual-review", "type": "HumanTask", "name": "Manual Review"},
                    {"id": "approve-request", "type": "HumanTask", "name": "Approve Request"}
                ]"""
            )

            val definition = ProcessDefinition(
                id = 10,
                processName = "approval",
                definitionJson = definitionJson,
                version = 1
            )

            val instance = ProcessInstance(
                id = processInstanceId,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("manual-review"),
                nodeHistory = listOf("manual-review")
            )

            val pendingSourceTask = Task(
                id = 1001,
                processInstanceId = processInstanceId,
                title = "Manual Review",
                nodeId = "manual-review"
            )

            every { mockProcessInstanceRepository.findById(processInstanceId) } returns Optional.of(instance)
            every { mockProcessInstanceRepository.findByIdForUpdate(processInstanceId) } returns instance
            every { mockObjectMapper.readTree(definitionJson) } returns internalJsonFromBpmn(definitionJson, objectMapper)

            every {
                mockTaskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
                    processInstanceId,
                    "manual-review",
                    com.easy.bpm.enum.TaskStatus.PENDING
                )
            } returns listOf(pendingSourceTask)

            every {
                mockTaskRepository.findByProcessInstanceIdAndNodeIdAndStatus(
                    processInstanceId,
                    "approve-request",
                    com.easy.bpm.enum.TaskStatus.PENDING
                )
            } returns emptyList()

            every { mockTaskRepository.delete(pendingSourceTask) } just runs

            every { mockFormService.getLatestVersionByName("approve-request") } returns null
            every { mockTaskRepository.save(any<Task>()) } answers {
                firstArg<Task>().copy(id = 2002)
            }

            every { mockProcessInstanceRepository.save(any<ProcessInstance>()) } answers { firstArg<ProcessInstance>() }

            // Act
            val result = processService.moveProcessNode(processInstanceId, "manual-review", "approve-request")

            // Assert
            result.currentNode shouldBe listOf("approve-request")
            result.nodeHistory shouldContain "approve-request"

            verify(exactly = 1) { mockHistoricVariableArchiver.archiveTaskVariables(pendingSourceTask) }
            verify(exactly = 1) { mockTaskRepository.delete(pendingSourceTask) }
            verify(exactly = 1) {
                mockTaskRepository.save(match<Task> {
                    it.processInstanceId == processInstanceId &&
                        it.nodeId == "approve-request" &&
                        it.title == "Approve Request"
                })
            }
        }
    }

    context("handleServiceTaskFailed") {
        test("should mark instance as FAILED when no attached error boundary exists") {
            // Arrange
            val processInstanceId = 77L
            val definitionJson = unitTestBpmnXml(
                "api-no-boundary",
                """[
                    {"id": "api-task", "type": "APITask", "next": ["end"]},
                    {"id": "end", "type": "EndEvent"}
                ]""",
                """[
                    {"from": "api-task", "to": "end", "condition": null}
                ]"""
            )

            val definition = ProcessDefinition(
                id = 30,
                processName = "api-no-boundary",
                definitionJson = definitionJson,
                version = 1
            )

            val instance = ProcessInstance(
                id = processInstanceId,
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("api-task"),
                nodeHistory = listOf("api-task")
            )

            every { mockProcessInstanceRepository.findById(processInstanceId) } returns Optional.of(instance)
            every { mockProcessInstanceRepository.findByIdForUpdate(processInstanceId) } returns instance
            every { mockObjectMapper.readTree(definitionJson) } returns internalJsonFromBpmn(definitionJson, objectMapper)
            every { mockProcessInstanceRepository.save(any<ProcessInstance>()) } answers { firstArg<ProcessInstance>() }

            // Act
            processService.handleServiceTaskFailed(processInstanceId, "api-task", "network timeout")

            // Assert
            instance.status shouldBe ProcessStatus.FAILED
            instance.currentNode shouldBe emptyList()
            verify(exactly = 1) { mockProcessInstanceRepository.save(match<ProcessInstance> { it.status == ProcessStatus.FAILED }) }
        }
    }

    context("getProcessDefinitionById") {
        test("should return process definition when it exists") {
            // Arrange
            val definitionId = 11L
            val definition = ProcessDefinition(id = definitionId, key = "order", processName = "order", definitionJson = "{}", version = 3)
            every { mockProcessDefinitionRepository.findById(definitionId) } returns Optional.of(definition)

            // Act
            val result = processService.getProcessDefinitionById(definitionId)

            // Assert
            result shouldBe definition
        }

        test("should return null when process definition does not exist") {
            // Arrange
            val definitionId = 999L
            every { mockProcessDefinitionRepository.findById(definitionId) } returns Optional.empty()

            // Act
            val result = processService.getProcessDefinitionById(definitionId)

            // Assert
            result shouldBe null
        }
    }
    }
}
