package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.process.ProcessService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import com.easy.bpm.messaging.RabbitPublisher
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID

@Transactional
class ParallelJoinIntegrationTest(
    @Autowired private val processes: ProcessService,
    @Autowired private val tasks: TaskService,
    @Autowired private val instances: ProcessInstanceRepository,
    @Autowired private val taskRepository: TaskRepository,
    @Autowired private val variables: ProcessVariableRepository,
    @Autowired private val historicVariables: com.easy.bpm.repository.variable.HistoricProcessVariableRepository,
    @Autowired private val events: ProcessInstanceEventRepository,
    @Autowired private val mapper: ObjectMapper
) : IntegrationTestBase() {
    @MockBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Test
    fun `exclusive routing into a parallel join counts one arrival`() {
        val definition = parallelDefinition("parallel-exclusive-arrival")
        (definition.get("nodes") as com.fasterxml.jackson.databind.node.ArrayNode)
            .add(mapper.readTree("""{"id":"route","type":"ExclusiveGateway"}"""))
        val flows = definition.get("flows") as com.fasterxml.jackson.databind.node.ArrayNode
        (flows.first { it.get("from").asText() == "left" } as com.fasterxml.jackson.databind.node.ObjectNode).put("to", "route")
        flows.add(mapper.readTree("""{"from":"route","to":"join","condition":"true"}"""))
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        val branches = taskRepository.findByProcessInstanceId(instance.id).associateBy { it.nodeId }
        tasks.completeTask(branches.getValue("left").id, "tester", emptyMap())
        assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.ACTIVE)
        tasks.completeTask(branches.getValue("right").id, "tester", emptyMap())
        assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.COMPLETED)
    }

    @Test
    fun `end event in one branch does not finish the remaining branch`() {
        val definition = parallelDefinition("parallel-independent-ends")
        val flows = definition.get("flows") as com.fasterxml.jackson.databind.node.ArrayNode
        flows.filter { it.get("to").asText() == "join" }.forEach {
            (it as com.fasterxml.jackson.databind.node.ObjectNode).put("to", "end")
        }
        val nodes = definition.get("nodes") as com.fasterxml.jackson.databind.node.ArrayNode
        nodes.remove(nodes.indexOfFirst { it.get("id").asText() == "join" })
        for (index in flows.size() - 1 downTo 0) {
            if (flows[index].get("from").asText() == "join") flows.remove(index)
        }
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        val branches = taskRepository.findByProcessInstanceId(instance.id).associateBy { it.nodeId }
        tasks.completeTask(branches.getValue("left").id, "tester", emptyMap())
        val waiting = instances.findById(instance.id).orElseThrow()
        assertThat(waiting.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(waiting.currentNode).containsExactly("right")
        assertThat(variables.findByProcessInstanceIdAndName(instance.id, "kept")).isNotNull
        tasks.completeTask(branches.getValue("right").id, "tester", emptyMap())
        assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id)
            .count { it.eventType == ProcessInstanceEventType.PROCESS_COMPLETED }).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ServiceTask", "MessageIntermediateCatchEvent", "TimerEvent"])
    fun `asynchronous branch completion preserves the other pending task`(type: String) {
        val definition = parallelDefinition("parallel-async-$type")
        val left = definition.get("nodes").first { it.get("id").asText() == "left" } as com.fasterxml.jackson.databind.node.ObjectNode
        left.put("type", type)
        left.set<com.fasterxml.jackson.databind.JsonNode>("properties", mapper.readTree("""{"timeoutSeconds":3600}"""))
        left.set<com.fasterxml.jackson.databind.JsonNode>("message", mapper.readTree(
            """{"name":"parallel-async-$type","correlationKeys":["case-69"],"payload":[]}"""))
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        when (type) {
            "ServiceTask" -> processes.handleServiceTaskCompleted(instance.id, "left", emptyMap())
            "TimerEvent" -> processes.handleTimerTimeout(instance.id, "left")
            else -> processes.handleMessageReceived("parallel-async-$type", "case-69", emptyMap())
        }
        val waiting = instances.findById(instance.id).orElseThrow()
        assertThat(waiting.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(waiting.currentNode).contains("right")
        assertThat(variables.findByProcessInstanceIdAndName(instance.id, "kept")).isNotNull
        val task = taskRepository.findByProcessInstanceId(instance.id).single()
        tasks.completeTask(task.id, "tester", emptyMap())
        assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.COMPLETED)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent completions serialize arrivals and reject duplicate task completion`(duplicate: Boolean) {
        val definition = parallelDefinition("parallel-concurrent-${UUID.randomUUID()}")
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        val branches = taskRepository.findByProcessInstanceId(instance.id).associateBy { it.nodeId }
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        try {
            val ids = listOf(branches.getValue("left").id, branches.getValue(if (duplicate) "left" else "right").id)
            val futures = ids.map { id -> executor.submit<Boolean> {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                try {
                    tasks.completeTask(id, "tester", emptyMap())
                    true
                } catch (_: IllegalStateException) {
                    false
                }
            } }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val successful = futures.count { it.get(20, TimeUnit.SECONDS) }
            assertThat(successful).isEqualTo(if (duplicate) 1 else 2)
            if (duplicate) {
                assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.ACTIVE)
                tasks.completeTask(branches.getValue("right").id, "tester", emptyMap())
            }
            assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.COMPLETED)
            assertThat(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id)
                .count { it.eventType == ProcessInstanceEventType.PROCESS_COMPLETED }).isEqualTo(1)
        } finally {
            start.countDown()
            executor.shutdownNow()
            processes.deleteProcessInstance(instance.id)
        }
    }

    @Test
    fun `join with three branches keeps waiting after two arrivals`() {
        val definition = parallelDefinition("parallel-three")
        (definition.get("nodes") as com.fasterxml.jackson.databind.node.ArrayNode).add(mapper.readTree(
            """{"id":"third","name":"Third","type":"HumanTask","config":{"assignee":"tester"}}"""))
        (definition.get("flows") as com.fasterxml.jackson.databind.node.ArrayNode).apply {
            add(mapper.readTree("""{"from":"fork","to":"third"}"""))
            add(mapper.readTree("""{"from":"third","to":"join"}"""))
        }
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        val branches = taskRepository.findByProcessInstanceId(instance.id).associateBy { it.nodeId }
        for (name in listOf("left", "right")) {
            tasks.completeTask(branches.getValue(name).id, "tester", emptyMap())
            assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.ACTIVE)
            assertThat(variables.findByProcessInstanceIdAndName(instance.id, "kept")).isNotNull
        }
        tasks.completeTask(branches.getValue("third").id, "tester", emptyMap())
        assertThat(instances.findById(instance.id).orElseThrow().status).isEqualTo(ProcessStatus.COMPLETED)
    }

    private fun parallelDefinition(key: String) = mapper.readTree("""
        {"processId":"$key","variables":[{"name":"kept","type":"string","initialValue":"active"}],
         "nodes":[{"id":"start","type":"StartEvent"},{"id":"fork","type":"ParallelGateway"},
         {"id":"left","name":"Left","type":"HumanTask","config":{"assignee":"tester"}},
         {"id":"right","name":"Right","type":"HumanTask","config":{"assignee":"tester"}},
         {"id":"join","type":"ParallelGateway"},{"id":"end","type":"EndEvent"}],
         "flows":[{"from":"start","to":"fork"},{"from":"fork","to":"left"},{"from":"fork","to":"right"},
         {"from":"left","to":"join"},{"from":"right","to":"join"},{"from":"join","to":"end"}]}
    """)

    @ParameterizedTest
    @ValueSource(strings = ["left", "right"])
    fun `join must wait for both human branches before completing`(first: String) {
        val definition = parallelDefinition("parallel-human-regression")
        val deployed = processes.deployProcess(legacyJsonFixtureToBpmnXml(definition))
        val instance = processes.startProcessInstance(deployed.id)
        val pending = taskRepository.findByProcessInstanceId(instance.id).associateBy { it.nodeId }
        assertThat(pending).hasSize(2)
        tasks.completeTask(pending.getValue(first).id, "tester", emptyMap())
        val waiting = instances.findById(instance.id).orElseThrow()
        assertThat(waiting.status).isEqualTo(ProcessStatus.ACTIVE)
        assertThat(waiting.nodeHistory).doesNotContain("end")
        assertThat(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id)
            .count { it.eventType == ProcessInstanceEventType.PROCESS_COMPLETED }).isZero()
        assertThat(variables.findByProcessInstanceIdAndName(instance.id, "kept")).isNotNull
        assertThat(variables.findByProcessInstanceIdAndName(instance.id, "__pg_join_arrivals")!!.value.asInt()).isEqualTo(1)
        val second = if (first == "left") "right" else "left"
        assertThat(waiting.currentNode).contains(second)
        assertThat(taskRepository.findById(pending.getValue(second).id).orElseThrow().status).isEqualTo(TaskStatus.PENDING)
        tasks.completeTask(pending.getValue(second).id, "tester", emptyMap())
        val completed = instances.findById(instance.id).orElseThrow()
        assertThat(completed.status).isEqualTo(ProcessStatus.COMPLETED)
        assertThat(completed.currentNode).isEmpty()
        assertThat(variables.findByProcessInstanceId(instance.id)).isEmpty()
        val archived = historicVariables.findByProcessInstanceId(instance.id)
        assertThat(archived.map { it.name }).doesNotContain("__pg_join_arrivals").contains("kept")
        assertThat(archived.single { it.name == "kept" }.value.asText()).isEqualTo("active")
        assertThat(completed.nodeHistory.count { it == "end" }).isEqualTo(1)
        assertThat(events.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(instance.id)
            .count { it.eventType == ProcessInstanceEventType.PROCESS_COMPLETED }).isEqualTo(1)
    }
}
