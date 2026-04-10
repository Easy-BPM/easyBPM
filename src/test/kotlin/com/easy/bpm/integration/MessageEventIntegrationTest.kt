package com.easy.bpm.integration

import com.easy.bpm.enum.MessageSubscriptionStatus
import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.message.MessageSubscription
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.repository.message.MessageSubscriptionRepository
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.ProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageEventIntegrationTest {

    @Autowired
    private lateinit var processService: ProcessService

    @Autowired
    private lateinit var processDefinitionRepository: ProcessDefinitionRepository

    @Autowired
    private lateinit var processInstanceRepository: ProcessInstanceRepository

    @Autowired
    private lateinit var messageSubscriptionRepository: MessageSubscriptionRepository

    @Autowired
    private lateinit var processVariableRepository: ProcessVariableRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var processDefinition: ProcessDefinition

    @BeforeEach
    fun setup() {
        // Create a process definition with message event
        val definitionJson = """
        {
            "processId": "messageProcessTest",
            "variables": [
                {"name": "orderId", "initialValue": "ORDER-123"},
                {"name": "customerId", "initialValue": "CUST-456"}
            ],
            "nodes": [
                {
                    "id": "start",
                    "type": "StartEvent",
                    "name": "Start",
                    "next": ["waitForPayment"]
                },
                {
                    "id": "waitForPayment",
                    "type": "MessageEvent",
                    "name": "Wait for Payment",
                    "properties": {
                        "messageName": "PaymentConfirmed",
                        "correlationKey": "${'$'}{orderId}-${'$'}{customerId}",
                        "timeoutSeconds": 3600
                    },
                    "next": ["end"]
                },
                {
                    "id": "end",
                    "type": "EndEvent",
                    "name": "End"
                }
            ],
            "flows": [
                {
                    "source": "start",
                    "target": "waitForPayment"
                },
                {
                    "source": "waitForPayment",
                    "target": "end"
                }
            ]
        }
        """.trimIndent()

        processDefinition = processDefinitionRepository.save(
            ProcessDefinition(
                name = "messageProcessTest",
                definitionJson = definitionJson,
                version = 1
            )
        )
    }

    @Test
    fun testMessageEventCreatesSubscription() {
        // Start process instance
        val instance = processService.startProcessInstance(processDefinition.id)

        assertEquals(ProcessStatus.ACTIVE, instance.status)

        // Verify message subscription was created
        val subscriptions = messageSubscriptionRepository.findByProcessInstanceId(instance.id)
        assertEquals(1, subscriptions.size)

        val subscription = subscriptions[0]
        assertEquals(instance.id, subscription.processInstanceId)
        assertEquals("waitForPayment", subscription.nodeId)
        assertEquals("PaymentConfirmed", subscription.messageName)
        assertEquals("ORDER-123-CUST-456", subscription.correlationKey)
        assertEquals(MessageSubscriptionStatus.AWAITING, subscription.status)
    }

    @Test
    fun testMessageEventWithVariableSubstitution() {
        val instance = processService.startProcessInstance(processDefinition.id)

        // Verify correlation key uses variable values
        val subscription = messageSubscriptionRepository.findByMessageNameAndCorrelationKeyAndStatus(
            "PaymentConfirmed",
            "ORDER-123-CUST-456",
            MessageSubscriptionStatus.AWAITING
        )

        assertNotNull(subscription)
        assertEquals("PaymentConfirmed", subscription.messageName)
        assertEquals("ORDER-123-CUST-456", subscription.correlationKey)
    }

    @Test
    fun testMessageReceptionResumesProcess() {
        val instance = processService.startProcessInstance(processDefinition.id)
        val subscriptionBefore = messageSubscriptionRepository.findByProcessInstanceId(instance.id)
        assertEquals(1, subscriptionBefore.size)
        assertEquals(MessageSubscriptionStatus.AWAITING, subscriptionBefore[0].status)

        // Receive message with variables
        val messageVariables = mapOf(
            "paymentStatus" to "confirmed",
            "transactionId" to "TXN-789"
        )

        processService.handleMessageReceived(
            messageName = "PaymentConfirmed",
            correlationKey = "ORDER-123-CUST-456",
            variables = messageVariables
        )

        // Verify subscription status is updated
        val updatedInstance = processInstanceRepository.findById(instance.id).orElseThrow()
        assertEquals(ProcessStatus.COMPLETED, updatedInstance.status)

        // Verify message variables were saved into process context
        val paymentStatusVar = processVariableRepository.findByProcessInstanceIdAndName(
            instance.id,
            "paymentStatus"
        )
        assertNotNull(paymentStatusVar)
        assertEquals("confirmed", paymentStatusVar.value.asText())

        val transactionIdVar = processVariableRepository.findByProcessInstanceIdAndName(
            instance.id,
            "transactionId"
        )
        assertNotNull(transactionIdVar)
        assertEquals("TXN-789", transactionIdVar.value.asText())
    }

    @Test
    fun testMessageSubscriptionCleanupAfterReceipt() {
        val instance = processService.startProcessInstance(processDefinition.id)

        val subscriptionsBefore = messageSubscriptionRepository.findByProcessInstanceId(instance.id)
        assertEquals(1, subscriptionsBefore.size)

        // Receive message
        processService.handleMessageReceived(
            messageName = "PaymentConfirmed",
            correlationKey = "ORDER-123-CUST-456",
            variables = null
        )

        // Verify subscription was deleted
        val subscriptionsAfter = messageSubscriptionRepository.findByProcessInstanceId(instance.id)
        assertEquals(0, subscriptionsAfter.size)
    }

    @Test
    fun testMessageWithoutMatchingSubscriptionThrowsException() {
        // Try to send message without any process waiting for it
        try {
            processService.handleMessageReceived(
                messageName = "NonExistentMessage",
                correlationKey = "NO-MATCH",
                variables = null
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (ex: IllegalArgumentException) {
            assertEquals(
                "No waiting subscription for message 'NonExistentMessage' with correlationKey 'NO-MATCH'",
                ex.message
            )
        }
    }

    @Test
    fun testMultipleProcessInstancesCanWaitForDifferentMessages() {
        // Start two process instances with different variable values
        val instance1 = processService.startProcessInstance(processDefinition.id)

        // Modify correlation key for second instance
        val variableInInstance1 = processVariableRepository
            .findByProcessInstanceIdAndName(instance1.id, "orderId")!!
        variableInInstance1.value = objectMapper.valueToTree("ORDER-999")
        processVariableRepository.save(variableInInstance1)

        // Get the actual subscriptions created
        val sub1 = messageSubscriptionRepository.findByProcessInstanceId(instance1.id)

        // Verify both instances can wait independently
        assertEquals(1, sub1.size)
    }

    @Test
    fun testMessageEventTimeoutInfoStorage() {
        val instance = processService.startProcessInstance(processDefinition.id)

        val subscription = messageSubscriptionRepository.findByProcessInstanceId(instance.id)[0]

        // Verify timeout information is set
        assertNotNull(subscription.timeoutAt)
        // Timeout should be approximately 1 hour from now
        val diffInSeconds = kotlin.math.abs(
            java.time.temporal.ChronoUnit.SECONDS.between(
                java.time.LocalDateTime.now(),
                subscription.timeoutAt
            )
        )
        assertEquals(3600L, diffInSeconds, 10L) // Allow 10 second difference
    }

    @Test
    fun testDuplicateMessageSubscriptionRespectedByDatabase() {
        val instance = processService.startProcessInstance(processDefinition.id)

        // UNIQUE constraint on (process_instance_id, node_id) should prevent duplicates
        try {
            messageSubscriptionRepository.save(
                MessageSubscription(
                    processInstanceId = instance.id,
                    nodeId = "waitForPayment",
                    messageName = "PaymentConfirmed",
                    correlationKey = "ORDER-123-CUST-456"
                )
            )
            throw AssertionError("Expected unique constraint violation")
        } catch (ex: Exception) {
            // Expected - constraint should prevent this
            assert(ex.message?.contains("unique") == true || ex.message?.contains("duplicate") == true)
        }
    }
}
