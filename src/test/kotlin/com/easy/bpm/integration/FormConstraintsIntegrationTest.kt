package com.easy.bpm.integration

import com.easy.bpm.controller.TaskController
import com.easy.bpm.enum.*
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.*
import com.easy.bpm.model.task.Task
import com.easy.bpm.repository.process.*
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.service.form.FormService
import com.easy.bpm.service.task.TaskService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean

class FormConstraintsIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var publisher: RabbitPublisher
    @Autowired lateinit var forms: FormService
    @Autowired lateinit var tasks: TaskService
    @Autowired lateinit var controller: TaskController
    @Autowired lateinit var taskRepository: TaskRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var variables: TaskVariableRepository
    @Autowired lateinit var events: ProcessInstanceEventRepository
    @Autowired lateinit var mapper: ObjectMapper

    private fun create(): Task {
        val form=forms.deploy("strict_${System.nanoTime()}","Strict",mapper.readTree("""{"type":"object","properties":{"code":{"type":"string","minLength":3,"maxLength":5,"pattern":"^[A-Z]+$"},"quantity":{"type":"integer","minimum":0,"maximum":100,"multipleOf":2},"approved":{"type":"boolean"},"choice":{"type":"number","enum":[0,2]}},"required":["code","quantity"]}"""))
        val definition=definitions.save(ProcessDefinition(key="strict_${System.nanoTime()}",version=1,definitionJson="""<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test"><process id="strict"><userTask id="review"/><endEvent id="end"/><sequenceFlow id="f" sourceRef="review" targetRef="end"/></process></definitions>"""))
        val instance=instances.save(ProcessInstance(processDefinition=definition,status=ProcessStatus.ACTIVE,currentNode=listOf("review")))
        return taskRepository.save(Task(processInstanceId=instance.id,nodeId="review",assignee="alice",formId=form.id))
    }

    @ParameterizedTest @ValueSource(strings=["{\"code\":\"ABC\",\"quantity\":-2}","{\"code\":\"ABC\",\"quantity\":102}","{\"code\":\"ABC\",\"quantity\":1.5}","{\"code\":\"ABC\",\"quantity\":3}","{\"code\":\"abc\",\"quantity\":2}","{\"code\":\"A\",\"quantity\":2}","{\"code\":\"ABCDEF\",\"quantity\":2}","{\"code\":\"ABC\",\"quantity\":\"2\"}","{\"code\":\"ABC\",\"quantity\":2,\"choice\":1}"])
    fun `invalid direct completion preserves state and saved draft`(json: String) {
        val task=create()
        tasks.saveTaskDraft(task.id,"alice",emptySet(),mapOf("code" to "SAVED","quantity" to 0,"approved" to false))
        val before=variables.findByTaskId(task.id).associate { it.name to it.value };val count=events.countByProcessInstanceId(task.processInstanceId)
        org.mockito.Mockito.clearInvocations(publisher)
        val submitted=mapper.readValue(json,Map::class.java)
        val response=controller.completeTask(task.id,mapOf("assignee" to "alice","variables" to submitted))
        assertEquals(400,response.statusCode.value())
        assertTrue((response.body as? String).orEmpty().contains("form field"))
        assertEquals(TaskStatus.PENDING,taskRepository.findById(task.id).orElseThrow().status)
        assertEquals(ProcessStatus.ACTIVE,instances.findById(task.processInstanceId).orElseThrow().status)
        assertEquals(listOf("review"),instances.findById(task.processInstanceId).orElseThrow().currentNode)
        assertEquals(before,variables.findByTaskId(task.id).associate { it.name to it.value })
        assertEquals(count,events.countByProcessInstanceId(task.processInstanceId))
        org.mockito.Mockito.verifyNoInteractions(publisher)
    }

    @ParameterizedTest @ValueSource(ints=[0,100])
    fun `boundary valid completion preserves false and numeric values`(quantity: Int) {
        val task=create();tasks.completeTask(task.id,"alice",mapOf("code" to "ABC","quantity" to quantity,"approved" to false,"choice" to 0))
        assertEquals(ProcessStatus.COMPLETED,instances.findById(task.processInstanceId).orElseThrow().status)
        assertEquals(quantity,tasks.getTaskResponseById(task.id)!!.variables["quantity"])
        assertEquals(false,tasks.getTaskResponseById(task.id)!!.variables["approved"])
    }

    @Test fun `invalid draft can be saved and corrected before completing using pinned schema`() {
        val task=create();val pinned=forms.getById(task.formId!!)!!
        forms.deploy(pinned.formId,pinned.name,mapper.readTree("""{"properties":{"quantity":{"minimum":1000}}}"""))
        tasks.saveTaskDraft(task.id,"alice",emptySet(),mapOf("code" to "a","quantity" to -1))
        assertEquals(TaskStatus.PENDING,taskRepository.findById(task.id).orElseThrow().status)
        tasks.saveTaskDraft(task.id,"alice",emptySet(),mapOf("code" to "ABC","quantity" to 0))
        tasks.completeTask(task.id,"alice",emptyMap())
        assertEquals(ProcessStatus.COMPLETED,instances.findById(task.processInstanceId).orElseThrow().status)
    }
    @Test fun `invalid shared task completion rolls back automatic assignment`() {
        val task=create();task.assignee=null;task.candidateUsers.add("alice");taskRepository.saveAndFlush(task)
        val response=controller.completeTask(task.id,mapOf("assignee" to "alice","variables" to mapOf("code" to "ABC","quantity" to -1)))
        assertEquals(400,response.statusCode.value())
        assertNull(taskRepository.findById(task.id).orElseThrow().assignee)
        assertEquals(TaskStatus.PENDING,taskRepository.findById(task.id).orElseThrow().status)
    }

}
