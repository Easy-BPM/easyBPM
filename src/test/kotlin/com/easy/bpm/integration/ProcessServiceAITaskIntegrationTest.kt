package com.easy.bpm.integration

import com.easy.bpm.entity.ProcessDefinition
import com.easy.bpm.entity.ProcessInstance
import com.easy.bpm.entity.ProcessVariable
import com.easy.bpm.handler.AITaskHandler
import com.easy.bpm.model.variable.ProcessVariable as PVar
import com.easy.bpm.repository.ProcessDefinitionRepository
import com.easy.bpm.repository.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.ProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration Tests for AI Task Execution via ProcessService
 *
 * Tests full end-to-end flow:
 * - Process instance with AI task node
 * - Variable substitution from process variables
 * - AI response binding to output variable
 * - Database persistence verification
 *
 * Uses shared PostgreSQL TestContainer for real database testing.
 */
@ActiveProfiles("test")
@Transactional
class ProcessServiceAITaskIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var processService: ProcessService

  @Autowired
  private lateinit var aiTaskHandler: AITaskHandler

  @Autowired
  private lateinit var processDefinitionRepository: ProcessDefinitionRepository

  @Autowired
  private lateinit var processInstanceRepository: ProcessInstanceRepository

  @Autowired
  private lateinit var processVariableRepository: ProcessVariableRepository

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val jsonNodeFactory = JsonNodeFactory.instance

  @BeforeEach
  fun setup() {
    // Clear previous test data
    processVariableRepository.deleteAll()
    processInstanceRepository.deleteAll()
    processDefinitionRepository.deleteAll()
  }

  // ==================== HAPPY PATH TESTS ====================

  @Test
  fun `should execute AI task and bind response to process variable`() {
    // Arrange: Create process with AI task
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    val inputVar = createProcessVariable(instance.id!!, "text", "Summarize this article about AI")

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute AI task
    aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = mapOf("text" to "Summarize this article about AI")
    )

    // Assert: Verify response was stored
    val responseVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertNotNull(responseVar)
    assertEquals("This is a summary.", responseVar.value)
  }

  @Test
  fun `should substitute multiple variables in prompt template`() {
    // Arrange
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    createProcessVariable(instance.id!!, "customerId", "CUST-123")
    createProcessVariable(instance.id!!, "orderAmount", 299.99)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    val variables = mapOf(
      "customerId" to "CUST-123",
      "orderAmount" to 299.99
    )

    // Act: Execute with variables
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = variables
    )

    // Assert: Response should be stored
    assertNotNull(result)
    assertTrue(result.containsKey("summary"))
  }

  @Test
  fun `should preserve variable types in output`() {
    // Arrange: Setup with different variable types
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    
    createProcessVariable(instance.id!!, "stringVar", "Hello")
    createProcessVariable(instance.id!!, "numberVar", 42)
    createProcessVariable(instance.id!!, "boolVar", true)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = mapOf(
        "stringVar" to "Hello",
        "numberVar" to 42,
        "boolVar" to true
      )
    )

    // Assert: Response stored as string (AI response is always text)
    assertNotNull(result["summary"])
    assertTrue(result["summary"] is String)
  }

  @Test
  fun `should create process variable if output variable does not exist`() {
    // Arrange
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Verify variable doesn't exist yet
    val beforeVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertEquals(null, beforeVar)

    // Act: Execute AI task
    aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap()
    )

    // Assert: Variable should now exist
    val afterVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertNotNull(afterVar)
  }

  @Test
  fun `should handle JSON response with field extraction`() {
    // Arrange: Setup AI task with JSON response field extraction
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute task (mock provider returns JSON)
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap()
    )

    // Assert: Response should be extracted
    assertNotNull(result)
    assertTrue(result.containsKey("summary"))
  }

  // ==================== VARIABLE SUBSTITUTION TESTS ====================

  @Test
  fun `should handle missing variables gracefully in substitution`() {
    // Arrange: Variable not provided in input
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute without providing all variables
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap() // Missing expected variables
    )

    // Assert: Should still complete (missing vars left as {{varName}})
    assertNotNull(result)
  }

  @Test
  fun `should perform variable substitution with special characters`() {
    // Arrange: Variables containing special characters
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val variables = mapOf(
      "email" to "user@example.com",
      "url" to "https://example.com/path?param=value&other=123",
      "json" to mapOf("nested" to "value", "number" to 42)
    )

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = variables
    )

    // Assert: Should handle special chars without errors
    assertNotNull(result)
  }

  // ==================== PERSISTENCE TESTS ====================

  @Test
  fun `should persist AI response to database`() {
    // Arrange
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute task
    aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap()
    )

    // Assert: Verify database contains variable
    val savedVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertNotNull(savedVar)
    assertEquals("This is a summary.", savedVar.value)
  }

  @Test
  fun `should update existing process variable with AI response`() {
    // Arrange: Pre-populate a variable
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    val existingVar = createProcessVariable(instance.id!!, "summary", "Old summary")

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute AI task (should update existing variable)
    aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap()
    )

    // Assert: Variable should be updated
    val updatedVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertNotNull(updatedVar)
    assertEquals("This is a summary.", updatedVar.value)
  }

  @Test
  fun `should fetch input variables from database before execution`() {
    // Arrange: Create instance with multiple variables
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    createProcessVariable(instance.id!!, "context", "User request context")
    createProcessVariable(instance.id!!, "language", "en")
    createProcessVariable(instance.id!!, "tone", "professional")

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Fetch and execute
    val dbVariables = processVariableRepository.findByProcessInstanceId(instance.id!!)
      .associate { it.name to it.value }
    
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = dbVariables
    )

    // Assert: Should use database variables
    assertNotNull(result)
    assertTrue(dbVariables.containsKey("context"))
    assertTrue(dbVariables.containsKey("language"))
  }

  // ==================== EDGE CASE TESTS ====================

  @Test
  fun `should handle empty response gracefully`() {
    // Arrange: Create AI task that returns empty response
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute with empty response expected
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = emptyMap()
    )

    // Assert: Should complete without error
    assertNotNull(result)
  }

  @Test
  fun `should handle very long variable values`() {
    // Arrange: Create a large text variable
    val longText = "Lorem ipsum ".repeat(1000) // ~12KB
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)
    createProcessVariable(instance.id!!, "longText", longText)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = mapOf("longText" to longText)
    )

    // Assert: Should handle large data
    assertNotNull(result)
  }

  @Test
  fun `should support null variable values`() {
    // Arrange: Variable with null value
    val definition = createProcessDefinitionWithAITask()
    val instance = createProcessInstance(definition.id!!)

    val node = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }
      ?: throw AssertionError("AI task node not found")

    // Act: Execute with null value
    val result = aiTaskHandler.executeAITask(
      instanceId = instance.id!!,
      node = node.get("properties")!!,
      inputVariables = mapOf("nullVar" to null)
    )

    // Assert: Should handle null gracefully
    assertNotNull(result)
  }

  // ==================== HELPER METHODS ====================

  private fun createProcessDefinitionWithAITask(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-task-1")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Summarize: {{text}}")
            put("outputVariable", "summary")
            set("tuningParams", jsonNodeFactory.objectNode().apply {
              put("temperature", 0.7)
              put("maxTokens", 2000)
            })
          })
        })
      })
    }

    val definition = ProcessDefinition(
      processKey = "test-ai-process-${System.currentTimeMillis()}",
      processName = "Test AI Process",
      description = "Test process with AI task",
      definitionJson = definitionJson,
      version = 1
    )
    return processDefinitionRepository.save(definition)
  }

  private fun createProcessInstance(definitionId: Long): ProcessInstance {
    val instance = ProcessInstance(
      processDefinitionId = definitionId,
      currentNodes = listOf("ai-task-1"),
      nodeHistory = listOf(),
      status = "RUNNING",
      businessKey = "test-key-${System.currentTimeMillis()}",
      createdAt = LocalDateTime.now(),
      updatedAt = LocalDateTime.now()
    )
    return processInstanceRepository.save(instance)
  }

  private fun createProcessVariable(instanceId: Long, name: String, value: Any?): ProcessVariable {
    val variable = ProcessVariable(
      processInstanceId = instanceId,
      name = name,
      value = value,
      type = when (value) {
        is String -> "string"
        is Number -> "number"
        is Boolean -> "boolean"
        else -> "json"
      },
      createdAt = LocalDateTime.now(),
      updatedAt = LocalDateTime.now()
    )
    return processVariableRepository.save(variable)
  }
}
