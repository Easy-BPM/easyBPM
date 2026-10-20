package com.easy.bpm.integration

import com.easy.bpm.enum.*
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.process.ProcessInstanceStarter
import com.easy.bpm.service.message.MessageEventInboxService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

class MessagePayloadMappingIntegrationTest : IntegrationTestBase() {
    @MockitoBean lateinit var publisher: RabbitPublisher
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var definitions: ProcessDefinitionRepository
    @Autowired lateinit var instances: ProcessInstanceRepository
    @Autowired lateinit var variables: ProcessVariableRepository
    @Autowired lateinit var history: HistoricProcessVariableRepository
    @Autowired lateinit var events: ProcessInstanceEventRepository
    @Autowired lateinit var subscriptions: MessageSubscriptionRepository
    @Autowired lateinit var starter: ProcessInstanceStarter
    @Autowired lateinit var inbox: MessageEventInboxService

    private fun start(payload: String, end: Boolean = true): Pair<Long, String> {
        val key = "map_${UUID.randomUUID()}"
        val definition = mapper.readTree("""{"processId":"$key","variables":[{"name":"caseId","initialValue":"$key"},{"name":"receivedPayload","initialValue":{}},{"name":"second","initialValue":"old"}],"nodes":[{"id":"start","type":"StartEvent","next":["wait"]},{"id":"wait","type":"MessageIntermediateCatchEvent","message":{"name":"$key","correlationKeys":["caseId"],"payload":$payload},"next":["after"]},{"id":"after","type":"${if(end) "EndEvent" else "UserTask"}","config":{"assignee":"alice"}}],"flows":[{"from":"start","to":"wait"},{"from":"wait","to":"after"}]}""")
        val deployed = definitions.save(ProcessDefinition(key=key, version=1, definitionJson=legacyJsonFixtureToBpmnXml(definition)))
        return starter.startWithDefinition(deployed).id to key
    }
    private val mapping = """[{"source":"variable","sourceValue":"payload","sourceName":"payload","target":"variable","targetVariable":"receivedPayload","value":"receivedPayload","type":"json"}]"""
    private fun payload(json: String): Map<String, Any> = mapper.readValue(json, Map::class.java) as Map<String, Any>
    private fun current(id: Long) = variables.findByProcessInstanceId(id).associate { it.name to it.value }

    @ParameterizedTest @ValueSource(strings=["{\"ok\":true,\"count\":0,\"enabled\":false}","[0,false,null]","0","false","null"])
    fun `mapped values survive completion and duplicate delivery`(json: String) {
        val (id,key)=start(mapping)
        val messageId=UUID.randomUUID().toString()
        val result=inbox.acceptExternalMessage(messageId,key,key,payload("""{"payload":$json}"""))
        assertEquals(MessageEventInboxStatus.PROCESSED,result.message.status)
        assertEquals(ProcessStatus.COMPLETED,instances.findById(id).orElseThrow().status)
        val stored=history.findByProcessInstanceId(id).associate { it.name to it.value }
        assertEquals(mapper.readTree(json),stored["receivedPayload"])
        assertFalse(stored.containsKey("payload"))
        val before=events.countByProcessInstanceId(id)
        assertTrue(inbox.acceptExternalMessage(messageId,key,key,payload("""{"payload":"changed"}""")).duplicate)
        assertEquals(before,events.countByProcessInstanceId(id))
        assertEquals(stored,history.findByProcessInstanceId(id).associate { it.name to it.value })
    }

    @Test fun `legacy empty mappings preserve incoming names and correlation isolation`() {
        val (id,key)=start("[]",false)
        val (other,otherKey)=start("[]",false)
        assertEquals(MessageEventInboxStatus.UNMATCHED,inbox.acceptExternalMessage(null,key,otherKey,mapOf("incoming" to false)).message.status)
        assertEquals(MessageEventInboxStatus.PROCESSED,inbox.acceptExternalMessage(null,key,key,mapOf("incoming" to false)).message.status)
        assertEquals(mapper.readTree("false"),current(id)["incoming"])
        assertFalse(current(other).containsKey("incoming"))
    }

    @ParameterizedTest @ValueSource(strings=["missing","type","target"])
    fun `invalid mappings roll back all writes and preserve waiting subscription`(failure: String) {
        val bad=when(failure){
            "missing" -> """{"source":"variable","sourceValue":"absent","targetVariable":"second","type":"json"}"""
            "type" -> """{"source":"variable","sourceValue":"payload","targetVariable":"second","type":"number"}"""
            else -> """{"source":"variable","sourceValue":"payload","targetVariable":"undeclared","type":"json"}"""
        }
        val (id,key)=start(mapping.dropLast(1)+","+bad+"]",false)
        val before=current(id);val eventCount=events.countByProcessInstanceId(id)
        val result=inbox.acceptExternalMessage(null,key,key,mapOf("payload" to mapOf("ok" to true)))
        assertEquals(MessageEventInboxStatus.FAILED,result.message.status)
        assertEquals(before,current(id))
        assertEquals(listOf("wait"),instances.findById(id).orElseThrow().currentNode)
        assertEquals(MessageSubscriptionStatus.AWAITING,subscriptions.findByProcessInstanceIdAndNodeId(id,"wait")!!.status)
        assertEquals(eventCount,events.countByProcessInstanceId(id))
    }

    @Test fun `aliases and static mapping preserve typed values`() {
        val (id,key)=start("""[{"source":"variable","sourceName":"payload","value":"receivedPayload","type":"boolean"},{"source":"static","sourceValue":"0","targetVariable":"second","type":"number"}]""",false)
        assertEquals(MessageEventInboxStatus.PROCESSED,inbox.acceptExternalMessage(null,key,key,mapOf("payload" to false)).message.status)
        assertEquals(mapper.readTree("false"),current(id)["receivedPayload"])
        assertEquals(mapper.readTree("0"),current(id)["second"])
    }
}
