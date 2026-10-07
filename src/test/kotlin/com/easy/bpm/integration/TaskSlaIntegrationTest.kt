package com.easy.bpm.integration

import com.easy.bpm.enum.*
import com.easy.bpm.model.process.*
import com.easy.bpm.model.task.Task
import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.*
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.task.*
import com.easy.bpm.controller.data.*
import com.easy.bpm.messaging.RabbitPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.TestPropertySource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestPropertySource(properties=["easybpm.sla.poll-ms=30000"])
class TaskSlaIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbit: RabbitPublisher
    @Autowired lateinit var sla: TaskSlaService
    @Autowired lateinit var query: TaskSlaQueryService
    @Autowired lateinit var tasks: TaskRepository
    @Autowired lateinit var taskService: TaskService
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var users: AppUserRepository
    @Autowired lateinit var variables: ProcessVariableRepository
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var processService: com.easy.bpm.service.process.ProcessService
    @Autowired lateinit var meters: io.micrometer.core.instrument.MeterRegistry
    private val now = Instant.parse("2026-03-08T06:30:00Z")
    private val created = mutableListOf<Long>()
    private fun <T> tx(block: () -> T): T = TransactionTemplate(transactionManager).execute { block() }!!
    private fun task(policy: String = """{"duration":"PT4H","timeZone":"America/New_York","alerts":[{"id":"warn","beforeDue":"PT30M"}]}""", owner: String? = null): Task = tx {
        val key = "sla-${UUID.randomUUID()}"
        val definition = definitions.saveAndFlush(ProcessDefinition(key=key, definitionJson="<xml/>"))
        val instance = instances.saveAndFlush(ProcessInstance(processDefinition=definition,status=ProcessStatus.ACTIVE))
        val task = tasks.saveAndFlush(Task(processInstanceId=instance.id,nodeId="review",assignee=owner))
        sla.configure(task, mapper.readTree("""{"config":{"sla":$policy}}"""), now)
        created += task.id
        task
    }
    @AfterEach fun cleanup() { tx { created.forEach { id -> jdbc.update("delete from task_sla_event where task_id=?",id); jdbc.update("delete from task_sla_action where task_id=?",id); tasks.deleteById(id) }; created.clear() } }

    @Test fun `persisted deadline XML snapshot and claim draft unclaim reassignment keep clock`() {
        val task=task(owner="alice")
        assertEquals(now.plusSeconds(14400),task.dueDate)
        assertEquals("America/New_York",task.slaTimeZone)
        val snapshot=task.slaPolicy
        taskService.saveTaskDraft(task.id,"alice",emptySet(),mapOf("approved" to false))
        taskService.unclaimTask(task.id,"alice",emptySet())
        taskService.claimTask(task.id,"bob",emptySet())
        taskService.reassignTask(task.id,"carol")
        val persisted=tasks.findById(task.id).orElseThrow()
        assertEquals(task.dueDate,persisted.dueDate); assertEquals(snapshot,persisted.slaPolicy)
        assertEquals(false, taskService.getTaskResponseById(task.id)!!.variables["approved"])
        assertNotNull(query.events(task.id,PageRequest.of(0,20)).content.single { it.eventType=="CONFIGURED" }.id)
    }
    @Test fun `two replicas publish one alert and rollback leaves no effect`() {
        val task=task(); val risk=task.slaRiskAt!!
        val alertsBefore=meters.find("task.sla.alerts").tag("outcome","success").counter()?.count() ?: 0.0
        assertThrows(IllegalStateException::class.java) { tx { sla.process(task.id,risk); throw IllegalStateException("rollback") } }
        assertEquals(alertsBefore,meters.find("task.sla.alerts").tag("outcome","success").counter()?.count() ?: 0.0)
        assertEquals(0L,jdbc.queryForObject("select count(*) from task_sla_event where task_id=? and event_type='ALERT'",Long::class.java,task.id))
        val pool=Executors.newFixedThreadPool(2); val ready=CountDownLatch(1)
        try {
            val jobs=(1..2).map { pool.submit { ready.await(); tx { sla.process(task.id,risk) } } }
            ready.countDown(); jobs.forEach { it.get(20,TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        tx { sla.process(task.id,risk.plusSeconds(1)) }
        assertEquals(1L,jdbc.queryForObject("select count(*) from task_sla_event where task_id=? and event_type='ALERT'",Long::class.java,task.id))
        assertEquals(1L,jdbc.queryForObject("select count(*) from task_sla_event where task_id=? and event_type='AT_RISK'",Long::class.java,task.id))
        assertEquals(alertsBefore+1.0,meters.find("task.sla.alerts").tag("outcome","success").counter()!!.count())
        assertTrue(meters.find("task.sla.overdue").gauge()!!.value() >= 1.0)
        meters.meters.filter{it.id.name.startsWith("task.sla")}.forEach { meter -> assertTrue(meter.id.tags.all { it.key=="outcome" && it.value in setOf("success","failure") }) }
    }
    @Test fun `missed alerts skipped and failed processes deferred without shifted deadline`() {
        val task=task()
        tx { val instance=instances.findByIdForUpdate(task.processInstanceId)!!;instance.status=ProcessStatus.FAILED;instances.save(instance);sla.process(task.id,task.dueDate!!) }
        assertEquals(0L,jdbc.queryForObject("select count(*) from task_sla_action where task_id=? and outcome<>'PENDING'",Long::class.java,task.id))
        tx { val instance=instances.findByIdForUpdate(task.processInstanceId)!!;instance.status=ProcessStatus.ACTIVE;instances.save(instance);sla.process(task.id,task.dueDate!!) }
        val events=query.events(task.id,PageRequest.of(0,20)).content
        assertFalse(events.any{it.eventType=="ALERT"});assertTrue(events.any{it.eventType=="SKIPPED" && it.actionId=="warn"});assertTrue(events.any{it.eventType=="OVERDUE"})
        assertEquals(task.dueDate,tasks.findById(task.id).orElseThrow().dueDate)
    }
    @Test fun `ordered overdue actions dynamic target failure retry and stale retry protection`() {
        val first="sla-first-${UUID.randomUUID()}"; val second="sla-second-${UUID.randomUUID()}"
        tx { users.save(AppUser(username=first,passwordHash="unused"));users.save(AppUser(username=second,passwordHash="unused")) }
        val task=task("""{"duration":"PT4H","escalations":[{"id":"dynamic","afterDue":"PT0S","action":"REASSIGN","target":"${'$'}{manager}"},{"id":"later","afterDue":"PT1H","action":"REASSIGN","target":"$second"}]}""")
        tx { sla.process(task.id,task.dueDate!!) }
        val incident=(jdbc.queryForObject("select incident_id from task_sla_action where task_id=? and action_id='dynamic'",Long::class.java,task.id))!!
        tx { variables.save(ProcessVariable(processInstanceId=task.processInstanceId,name="manager",value=mapper.valueToTree(first))) }
        val resolved=sla.retry(incident,"operator")
        assertEquals(com.easy.bpm.model.incident.IncidentStatus.RESOLVED,resolved.status)
        assertEquals(first,tasks.findById(task.id).orElseThrow().assignee)
        assertThrows(ResponseStatusException::class.java) { sla.retry(incident,"operator") }
        tx { sla.process(task.id,task.dueDate!!.plusSeconds(3600)) }
        assertEquals(second,tasks.findById(task.id).orElseThrow().assignee)
        assertEquals(task.dueDate,tasks.findById(task.id).orElseThrow().dueDate)
        assertEquals(ProcessStatus.ACTIVE,instances.findById(task.processInstanceId).orElseThrow().status)
        assertEquals(2L,jdbc.queryForObject("select count(*) from task_sla_event where task_id=? and event_type='ESCALATED'",Long::class.java,task.id))
    }
    @Test fun `completion lock wins and scheduler cannot mutate completed task`() {
        val task=task("""{"duration":"PT4H","escalations":[{"id":"e","afterDue":"PT0S","action":"REASSIGN","target":"missing"}]}""")
        val locked=CountDownLatch(1);val release=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val completion=pool.submit { tx { val current=tasks.findByIdForUpdate(task.id).orElseThrow();locked.countDown();release.await();current.status=TaskStatus.COMPLETED;current.completedWithinSla=true;current.slaCompletedAt=task.dueDate;tasks.saveAndFlush(current) } }
            assertTrue(locked.await(10,TimeUnit.SECONDS))
            val escalation=pool.submit { tx { sla.process(task.id,task.dueDate!!) } }
            release.countDown();completion.get(20,TimeUnit.SECONDS);escalation.get(20,TimeUnit.SECONDS)
        } finally { release.countDown();pool.shutdownNow() }
        assertEquals(TaskStatus.COMPLETED,tasks.findById(task.id).orElseThrow().status)
        assertEquals(0L,jdbc.queryForObject("select count(*) from task_sla_event where task_id=? and event_type in ('ESCALATED','ESCALATION_FAILED','OVERDUE')",Long::class.java,task.id))
    }
    @Test fun `fresh status inclusive ranges null last tie sorting visibility summaries and metrics`() {
        val first=task(owner="visible");val second=task(owner="hidden")
        val noSla=tx{tasks.saveAndFlush(Task(processInstanceId=first.processInstanceId,nodeId="legacy",assignee="visible")).also{created+=it.id}}
        val filter=listOf(TaskSearchFilterDto("slaStatus",value="OVERDUE"),TaskSearchFilterDto("dueDate",TaskFilterOperator.GREATER_THAN_OR_EQUAL,first.dueDate.toString()),TaskSearchFilterDto("dueDate",TaskFilterOperator.LESS_THAN_OR_EQUAL,first.dueDate.toString()))
        val page=taskService.searchVisibleTaskResponses("visible",emptySet(),filter,PageRequest.of(0,20,Sort.by("dueDate")))
        assertEquals(listOf(first.id),page.content.map{it.id});assertEquals(SlaStatus.OVERDUE,page.content.single().slaStatus)
        val summary=query.summary(emptyList(),TaskVisibility("visible",emptySet()))
        assertEquals(1L,summary.overdue);assertEquals(1L,summary.withoutSla)
        val sorted=taskService.searchVisibleTaskResponses("visible",emptySet(),emptyList(),PageRequest.of(0,20,Sort.by(Sort.Direction.DESC,"dueDate")))
        assertEquals(noSla.id,sorted.content.last().id)
        val metrics=query.metrics("USER",first.dueDate.toString(),first.dueDate.toString(),PageRequest.of(0,100))
        assertTrue(metrics.content.any{it.key=="visible" && it.overdue==1L});assertTrue(metrics.content.any{it.key=="hidden" && it.overdue==1L})
        assertThrows(IllegalArgumentException::class.java) { query.metrics("USER",second.dueDate!!.plusSeconds(1).toString(),first.dueDate.toString(),PageRequest.of(0,20)) }
    }
    private fun processXml(policy: String?): String = legacyJsonFixtureToBpmnXml(mapper.readTree("""{
        "processId":"sla-flow-${UUID.randomUUID()}","nodes":[
          {"id":"start","name":"Start","type":"StartEvent"},
          {"id":"review","name":"Review","type":"HumanTask","config":${if(policy==null) "{}" else """{"sla":$policy}"""}},
          {"id":"end","name":"End","type":"EndEvent"}],
        "flows":[{"from":"start","to":"review"},{"from":"review","to":"end"}]
    }"""))
    @Test fun `deployed XML creates fixed and variable deadlines completes without changing clock and legacy executes`() {
        val definition=processService.deployProcess(processXml("""{"duration":"${'$'}{slaDuration}","timeZone":"America/Sao_Paulo"}"""))
        val before=Instant.now()
        val instance=processService.startProcessInstance(definition.key,mapOf("slaDuration" to "PT4H"))
        val after=Instant.now()
        val task=tasks.findByProcessInstanceId(instance.id).single();created+=task.id
        assertTrue(task.dueDate!! >= before.plusSeconds(14400) && task.dueDate!! <= after.plusSeconds(14400))
        val original=task.dueDate
        taskService.completeTask(task.id,"alice",emptySet(),mapOf("approved" to true))
        val complete=tasks.findById(task.id).orElseThrow()
        assertEquals(original,complete.dueDate);assertTrue(complete.completedWithinSla!!)
        assertEquals(SlaStatus.COMPLETED,taskService.getTaskResponseById(task.id)!!.slaStatus)
        assertEquals(ProcessStatus.COMPLETED,instances.findById(instance.id).orElseThrow().status)
        val legacy=processService.deployProcess(processXml(null))
        val legacyInstance=processService.startProcessInstance(legacy.key)
        val legacyTask=tasks.findByProcessInstanceId(legacyInstance.id).single();created+=legacyTask.id
        assertNull(legacyTask.dueDate);taskService.completeTask(legacyTask.id,"alice",emptySet(),emptyMap())
        assertEquals(ProcessStatus.COMPLETED,instances.findById(legacyInstance.id).orElseThrow().status)
    }
    @Test fun `invalid deployment has node-specific feedback and missing runtime variable creates incident without dangling task`() {
        val error=assertThrows(IllegalArgumentException::class.java) { processService.deployProcess(processXml("""{"duration":"P1M"}""")) }
        assertTrue(error.message!!.contains("review config.sla"))
        val definition=processService.deployProcess(processXml("""{"duration":"${'$'}{missingDuration}"}"""))
        val instance=processService.startProcessInstance(definition.key)
        assertEquals(ProcessStatus.FAILED,instance.status)
        assertTrue(tasks.findByProcessInstanceId(instance.id).isEmpty())
        assertTrue(jdbc.queryForObject("select count(*) from incident where process_instance_id=?",Long::class.java,instance.id)!!>0)
    }
    @Test fun `candidate union same-time order completed averages and stale reassignment retry`() {
        val successBefore=meters.find("task.sla.escalations").tag("outcome","success").counter()?.count() ?: 0.0
        val failureBefore=meters.find("task.sla.escalations").tag("outcome","failure").counter()?.count() ?: 0.0
        val username="candidate-${UUID.randomUUID()}"; val group="group-${UUID.randomUUID()}"
        tx{users.save(AppUser(username=username,passwordHash="unused",identityProvider="OIDC"));jdbc.update("insert into app_group(code,name,created_at,updated_at) values (?,?,current_timestamp,current_timestamp)",group,group)}
        val task=task("""{"duration":"PT4H","alerts":[{"id":"warn","beforeDue":"PT30M"}],"escalations":[{"id":"u","afterDue":"PT0S","action":"ADD_CANDIDATE_USER","target":"$username"},{"id":"g","afterDue":"PT0S","action":"ADD_CANDIDATE_GROUP","target":"$group"},{"id":"r","afterDue":"PT0S","action":"REASSIGN","target":"missing-user"}]}""")
        tx{sla.process(task.id,task.slaRiskAt!!)}
        tx{sla.process(task.id,task.dueDate!!)}
        val current=tasks.findById(task.id).orElseThrow()
        assertEquals(setOf(username),current.candidateUsers);assertEquals(setOf(group),current.candidateGroups)
        val incident=jdbc.queryForObject("select incident_id from task_sla_action where task_id=? and action_id='r'",Long::class.java,task.id)!!
        taskService.reassignTask(task.id,"manual-owner")
        assertEquals(409,assertThrows(ResponseStatusException::class.java){sla.retry(incident,"operator")}.statusCode.value())
        tx{val completed=tasks.findByIdForUpdate(task.id).orElseThrow();completed.status=TaskStatus.COMPLETED;completed.slaCompletedAt=task.slaRiskAt!!.plusSeconds(60);completed.completedWithinSla=true;tasks.saveAndFlush(completed)}
        val row=query.metrics("TASK",task.dueDate.toString(),task.dueDate.toString(),PageRequest.of(0,100)).content.single{it.key.endsWith(":review") && it.totalSlaCompleted==1L}
        assertEquals(1L,row.alertToCompletionSampleCount);assertEquals(60.0,row.alertToCompletionAverageSeconds);assertEquals(1.0,row.completedWithinSlaRate)
        assertEquals(2L,row.successfulEscalations);assertEquals(1L,row.failedEscalations)
        assertEquals(successBefore+2.0,meters.find("task.sla.escalations").tag("outcome","success").counter()!!.count())
        assertEquals(failureBefore+1.0,meters.find("task.sla.escalations").tag("outcome","failure").counter()!!.count())
    }
    @Test fun `cancelled and completed processes skip pending actions without transitions or assignment`() {
        listOf(ProcessStatus.CANCELLED,ProcessStatus.COMPLETED).forEach { state ->
            val task=task("""{"duration":"PT4H","alerts":[{"id":"warn","beforeDue":"PT30M"}],"escalations":[{"id":"e","afterDue":"PT0S","action":"REASSIGN","target":"missing"}]}""")
            tx { val instance=instances.findByIdForUpdate(task.processInstanceId)!!;instance.status=state;instances.saveAndFlush(instance);sla.process(task.id,task.dueDate!!) }
            val events=query.events(task.id,PageRequest.of(0,100)).content
            assertFalse(events.any{it.eventType in setOf("AT_RISK","OVERDUE","ALERT","ESCALATED","ESCALATION_FAILED")})
            assertEquals(4,events.count{it.eventType=="SKIPPED"})
            assertNull(tasks.findById(task.id).orElseThrow().assignee)
        }
    }
}
