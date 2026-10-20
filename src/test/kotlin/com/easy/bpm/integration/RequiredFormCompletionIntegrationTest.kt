package com.easy.bpm.integration

import com.easy.bpm.controller.TaskController
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean

class RequiredFormCompletionIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var rabbitPublisher: RabbitPublisher
    @Autowired lateinit var tasks: TaskService
    @Autowired lateinit var taskRepository: TaskRepository
    @Autowired lateinit var variables: TaskVariableRepository
    @Autowired lateinit var processVariables: ProcessVariableRepository
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var events: ProcessInstanceEventRepository
    @Autowired lateinit var forms: FormService
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var controller: TaskController

    private fun createTask(): Task {
        val form = forms.deploy("required_${System.nanoTime()}", "Required test", mapper.readTree(
            """{"type":"object","properties":{"qaDescription":{"type":"string"},"qaAmount":{"type":"number"},"qaApproved":{"type":"boolean"}},"required":["qaDescription","qaAmount","qaApproved"]}"""))
        val definition = definitions.save(ProcessDefinition(key = "required-${System.nanoTime()}", processName = "Required test", description = "Required test", version = 1,
            definitionJson = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test"><process id="required_test" isExecutable="true"><userTask id="review"/><endEvent id="end"/><sequenceFlow id="flow" sourceRef="review" targetRef="end"/></process></definitions>"""))
        val instance = instances.save(ProcessInstance(processDefinition = definition, status = ProcessStatus.ACTIVE, currentNode = listOf("review")))
        return taskRepository.save(Task(processInstanceId = instance.id, nodeId = "review", assignee = "alice", formId = form.id))
    }

    private fun reject(task: Task, submitted: Map<String, Any?>) {
        val before = variables.findByTaskId(task.id).associate { it.name to it.value }
        val processBefore = processVariables.findByProcessInstanceId(task.processInstanceId).associate { it.name to it.value }
        val eventCount = events.countByProcessInstanceId(task.processInstanceId)
        clearInvocations(rabbitPublisher)
        val response = controller.completeTask(task.id, mapOf("assignee" to "alice", "variables" to submitted))
        assertEquals(400, response.statusCode.value())
        assertTrue(response.body.orEmpty().contains("qaDescription"))
        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id).orElseThrow().status)
        val instance = instances.findById(task.processInstanceId).orElseThrow()
        assertEquals(ProcessStatus.ACTIVE, instance.status)
        assertEquals(listOf("review"), instance.currentNode)
        assertEquals(before, variables.findByTaskId(task.id).associate { it.name to it.value })
        assertEquals(processBefore, processVariables.findByProcessInstanceId(task.processInstanceId).associate { it.name to it.value })
        assertEquals(task.assignee, taskRepository.findById(task.id).orElseThrow().assignee)
        assertEquals(eventCount, events.countByProcessInstanceId(task.processInstanceId))
        verifyNoInteractions(rabbitPublisher)
    }

    @Test fun `omitted required input is rejected without changing state`() {
        val task = createTask()
        processVariables.save(ProcessVariable(processInstanceId = task.processInstanceId, name = "qaDescription", value = mapper.valueToTree("")))
        reject(task, mapOf("qaAmount" to 0, "qaApproved" to false))
    }

    @ParameterizedTest @NullSource @ValueSource(strings = ["", " \t\n"])
    fun `null empty and whitespace text cannot override valid draft`(value: String?) {
        val task = createTask()
        tasks.saveTaskDraft(task.id, "alice", emptySet(), mapOf("qaDescription" to "saved", "qaAmount" to 0, "qaApproved" to false))
        reject(task, mapOf("qaDescription" to value, "qaAmount" to 99))
    }

    @Test fun `incomplete draft is allowed but cannot complete`() {
        val task = createTask()
        tasks.saveTaskDraft(task.id, "alice", emptySet(), mapOf("qaDescription" to "", "qaAmount" to 0, "qaApproved" to false))
        reject(task, mapOf("qaAmount" to 0, "qaApproved" to false))
    }

    @Test fun `valid draft can complete without resending fields using pinned form version`() {
        val task = createTask()
        val pinned = forms.getById(task.formId!!)!!
        forms.deploy(pinned.formId, pinned.name, mapper.readTree("""{"required":["newRequiredField"]}"""))
        tasks.saveTaskDraft(task.id, "alice", emptySet(), mapOf("qaDescription" to "saved", "qaAmount" to 0, "qaApproved" to false))
        tasks.completeTask(task.id, "alice", emptyMap())
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(task.id).orElseThrow().status)
        assertEquals(ProcessStatus.COMPLETED, instances.findById(task.processInstanceId).orElseThrow().status)
        assertEquals("saved", tasks.getTaskResponseById(task.id)!!.variables["qaDescription"])
        assertEquals(0, tasks.getTaskResponseById(task.id)!!.variables["qaAmount"])
        assertEquals(false, tasks.getTaskResponseById(task.id)!!.variables["qaApproved"])
    }

    @Test fun `valid submitted input completes and preserves zero and false`() {
        val task = createTask()
        tasks.saveTaskDraft(task.id, "alice", emptySet(), mapOf("qaDescription" to " "))
        tasks.completeTask(task.id, "alice", mapOf("qaDescription" to "valid", "qaAmount" to 0, "qaApproved" to false))
        assertEquals(ProcessStatus.COMPLETED, instances.findById(task.processInstanceId).orElseThrow().status)
        assertEquals(false, tasks.getTaskResponseById(task.id)!!.variables["qaApproved"])
    }
    @Test fun `wrong required field type is rejected`() {
        reject(createTask(), mapOf("qaDescription" to 42, "qaAmount" to 0, "qaApproved" to false))
    }

    @Test fun `invalid completion cannot automatically claim a shared task`() {
        val task = createTask()
        task.assignee = null
        task.candidateUsers.add("alice")
        taskRepository.saveAndFlush(task)
        reject(task, emptyMap())
    }

    @Test fun `unauthorized user cannot complete or inspect form validation`() {
        val task = createTask()
        val response = controller.completeTask(task.id, mapOf("assignee" to "bob", "variables" to emptyMap<String, Any?>()))
        assertEquals(403, response.statusCode.value())
        assertEquals("alice", taskRepository.findById(task.id).orElseThrow().assignee)
        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id).orElseThrow().status)
    }

    @Test fun `HTTP completion returns actionable validation error`() {
        val task = createTask()
        val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tasks/{id}/complete", task.id)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("""{"assignee":"alice","variables":{"qaAmount":0,"qaApproved":false}}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("qaDescription")))
        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id).orElseThrow().status)
    }

    @Test fun `tasks without forms retain their completion contract`() {
        val task = createTask()
        taskRepository.saveAndFlush(task.copy(formId = null))
        tasks.completeTask(task.id, "alice", emptyMap())
        assertEquals(ProcessStatus.COMPLETED, instances.findById(task.processInstanceId).orElseThrow().status)
    }

    @Test fun `missing pinned form blocks completion safely`() {
        val task = createTask()
        taskRepository.saveAndFlush(task.copy(formId = Long.MAX_VALUE))
        val response = controller.completeTask(task.id, mapOf("assignee" to "alice"))
        assertEquals(409, response.statusCode.value())
        assertEquals("The task form is unavailable. Contact an administrator.", response.body)
        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id).orElseThrow().status)
    }

}
