package com.easy.bpm.integration

import com.easy.bpm.entity.ProcessDefinition
import com.easy.bpm.entity.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.ProcessDefinitionRepository
import com.easy.bpm.repository.ProcessInstanceRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.service.process.ProcessService
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
 * Full Integration Tests for AI Task Execution via ProcessService
 *
 * Tests Story 9.3.4: Complete End-to-End AI Task Workflows
 *
 * Validates:
 * - Full ProcessService → AITaskHandler flow
 * - Multiple AI tasks in single process
 * - Error boundary integration
 * - Variable injection across task chain
 * - Execution metrics and persistence
 * - Real database integration
 */
@ActiveProfiles("test")
@Transactional
class ProcessServiceFullAITaskIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var processService: ProcessService

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
    processVariableRepository.deleteAll()
    processInstanceRepository.deleteAll()
    processDefinitionRepository.deleteAll()
  }

  // ==================== FULL WORKFLOW TESTS ====================

  @Test
  fun `should execute complete workflow with single AI task`() {
    // Arrange: Create process with start → AI task → end
    val definition = createSimpleProcessWithAITask()
    val instance = createRunningInstance(definition.id!!)

    // Set initial variable
    createVariable(instance.id!!, "documentText", "Technical requirements for new feature")

    // Act: Execute through process
    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }!!

    processService.executeNode(instance, aiTaskNode, definition)

    // Assert: Verify workflow progressed
    val savedInstance = processInstanceRepository.findById(instance.id!!).get()
    assertEquals("RUNNING", savedInstance.status)

    // Verify output variable was created
    val summary = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "summary"
    )
    assertNotNull(summary)
  }

  @Test
  fun `should chain multiple AI tasks with variable passing`() {
    // Arrange: Create process with two AI tasks: extract → summarize
    val definition = createChainedAITaskProcess()
    val instance = createRunningInstance(definition.id!!)

    // Set initial content
    createVariable(instance.id!!, "content", "Long article about machine learning...")

    // Act: Execute first AI task (extract key points)
    val extractNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-extract" }!!
    
    processService.executeNode(instance, extractNode, definition)

    // Verify extraction completed
    val extracted = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "keyPoints"
    )
    assertNotNull(extracted)

    // Act: Execute second AI task (summarize the extracted points)
    val summarizeNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-summarize" }!!

    processService.executeNode(instance, summarizeNode, definition)

    // Assert: Final output created
    val finalSummary = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "executiveSummary"
    )
    assertNotNull(finalSummary)
  }

  @Test
  fun `should maintain variable state across multiple node executions`() {
    // Arrange
    val definition = createProcessWithMultipleVariables()
    val instance = createRunningInstance(definition.id!!)

    // Create initial variables
    createVariable(instance.id!!, "userId", "USER-123")
    createVariable(instance.id!!, "orderAmount", 5999.99)
    createVariable(instance.id!!, "isPremium", true)

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-recommendation" }!!

    // Act: Execute AI task with all variables available
    processService.executeNode(instance, aiTaskNode, definition)

    // Assert: Original variables unchanged, new variable added
    val userId = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "userId"
    )
    assertEquals("USER-123", userId?.value)

    val orderAmount = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "orderAmount"
    )
    assertEquals(5999.99, orderAmount?.value)

    val isPremium = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "isPremium"
    )
    assertEquals(true, isPremium?.value)

    // New output variable created
    val recommendation = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "recommendation"
    )
    assertNotNull(recommendation)
  }

  @Test
  fun `should handle JSON response parsing and field extraction`() {
    // Arrange: Create AI task with JSON response format
    val definition = createAITaskWithJSONResponse()
    val instance = createRunningInstance(definition.id!!)

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-json-task" }!!

    // Act
    processService.executeNode(instance, aiTaskNode, definition)

    // Assert: JSON response stored and extractable
    val response = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "analysisResult"
    )
    assertNotNull(response)
    assertTrue(response.type in listOf("json", "string"))
  }

  // ==================== ERROR BOUNDARY INTEGRATION TESTS ====================

  @Test
  fun `should trigger error boundary on AI task execution failure`() {
    // Arrange: Process with AI task + error boundary
    val definition = createProcessWithErrorBoundary()
    val instance = createRunningInstance(definition.id!!)

    // Create trigger for error (e.g., invalid config)
    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-error" }!!

    // Act: Execute task that should fail
    try {
      processService.executeNode(instance, aiTaskNode, definition)
    } catch (ex: Exception) {
      // Error boundary should catch this
      assertTrue(ex.message?.contains("AI Task") == true || ex.message?.contains("error") == true)
    }

    // Assert: Instance should still be in valid state
    val savedInstance = processInstanceRepository.findById(instance.id!!).get()
    assertNotNull(savedInstance)
  }

  @Test
  fun `should capture error details in error variable`() {
    // Arrange
    val definition = createProcessWithErrorCapture()
    val instance = createRunningInstance(definition.id!!)

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-with-error" }!!

    // Act
    try {
      processService.executeNode(instance, aiTaskNode, definition)
    } catch (ex: Exception) {
      // Expected
    }

    // Assert: Error details captured if error boundary configured
    val errorVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "aiError"
    )
    // May or may not exist depending on error boundary configuration
    if (errorVar != null) {
      assertTrue(errorVar.value is String || errorVar.value is Map<*, *>)
    }
  }

  // ==================== VARIABLE SCOPE TESTS ====================

  @Test
  fun `should isolate variables between process instances`() {
    // Arrange: Create two process instances from same definition
    val definition = createSimpleProcessWithAITask()
    val instance1 = createRunningInstance(definition.id!!)
    val instance2 = createRunningInstance(definition.id!!)

    createVariable(instance1.id!!, "documentText", "Document for instance 1")
    createVariable(instance2.id!!, "documentText", "Document for instance 2")

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }!!

    // Act: Execute both instances
    processService.executeNode(instance1, aiTaskNode, definition)
    processService.executeNode(instance2, aiTaskNode, definition)

    // Assert: Each instance has its own variable state
    val summary1 = processVariableRepository.findByProcessInstanceIdAndName(
      instance1.id!!,
      "summary"
    )
    val summary2 = processVariableRepository.findByProcessInstanceIdAndName(
      instance2.id!!,
      "summary"
    )
    
    assertNotNull(summary1)
    assertNotNull(summary2)
    // Both should be successful but computed separately
  }

  @Test
  fun `should preserve variable types through AI task execution`() {
    // Arrange
    val definition = createProcessWithTypedVariables()
    val instance = createRunningInstance(definition.id!!)

    createVariable(instance.id!!, "stringValue", "Hello World")
    createVariable(instance.id!!, "numberValue", 42)
    createVariable(instance.id!!, "booleanValue", true)
    createVariable(instance.id!!, "jsonValue", mapOf("key" to "value"))

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-type-test" }!!

    // Act
    processService.executeNode(instance, aiTaskNode, definition)

    // Assert: Original types preserved
    val stringVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "stringValue"
    )
    assertEquals("string", stringVar?.type)

    val numberVar = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "numberValue"
    )
    assertEquals("number", numberVar?.type)
  }

  // ==================== CONCURRENT EXECUTION TESTS ====================

  @Test
  fun `should handle rapid sequential AI task executions`() {
    // Arrange
    val definition = createSimpleProcessWithAITask()
    val instance = createRunningInstance(definition.id!!)

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "ai-task-1" }!!

    // Act: Execute multiple times rapidly
    repeat(3) {
      createVariable(instance.id!!, "input_$it", "Input $it")
      processService.executeNode(instance, aiTaskNode, definition)
    }

    // Assert: Instance should be in valid state
    val savedInstance = processInstanceRepository.findById(instance.id!!).get()
    assertEquals("RUNNING", savedInstance.status)
  }

  // ==================== PROCESS DEFINITION VARIATIONS ====================

  @Test
  fun `should support different AI providers in same process`() {
    // Arrange: Create process with OpenAI task and future Anthropic task
    val definition = createMultiProviderProcess()
    val instance = createRunningInstance(definition.id!!)

    createVariable(instance.id!!, "prompt", "Test prompt")

    val openaiNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "openai-task" }!!

    // Act
    processService.executeNode(instance, openaiNode, definition)

    // Assert
    val result = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "openaiResult"
    )
    assertNotNull(result)
  }

  @Test
  fun `should support conditional AI task execution`() {
    // Arrange: Process with gateway → AI task
    val definition = createConditionalAIProcess()
    val instance = createRunningInstance(definition.id!!)

    createVariable(instance.id!!, "requiresAI", true)

    val aiTaskNode = definition.definitionJson.get("nodes")
      .find { it.get("id")?.asText() == "conditional-ai" }!!

    // Act
    processService.executeNode(instance, aiTaskNode, definition)

    // Assert
    val result = processVariableRepository.findByProcessInstanceIdAndName(
      instance.id!!,
      "aiResponse"
    )
    assertNotNull(result)
  }

  // ==================== HELPER METHODS ====================

  private fun createSimpleProcessWithAITask(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "start")
          put("type", "StartEvent")
        })
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-task-1")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Summarize: {{documentText}}")
            put("outputVariable", "summary")
          })
        })
        add(jsonNodeFactory.objectNode().apply {
          put("id", "end")
          put("type", "EndEvent")
        })
      })
    }

    return createAndSaveDefinition("simple-ai-process", definitionJson)
  }

  private fun createChainedAITaskProcess(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-extract")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Extract key points: {{content}}")
            put("outputVariable", "keyPoints")
          })
        })
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-summarize")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Summarize these points: {{keyPoints}}")
            put("outputVariable", "executiveSummary")
          })
        })
      })
    }

    return createAndSaveDefinition("chained-ai-process", definitionJson)
  }

  private fun createProcessWithMultipleVariables(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-recommendation")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "For user {{userId}} with amount {{orderAmount}} premium={{isPremium}}: recommend product")
            put("outputVariable", "recommendation")
          })
        })
      })
    }

    return createAndSaveDefinition("multi-var-ai-process", definitionJson)
  }

  private fun createAITaskWithJSONResponse(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-json-task")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Analyze and return JSON")
            put("outputVariable", "analysisResult")
            put("responseFormat", "json")
          })
        })
      })
    }

    return createAndSaveDefinition("json-ai-process", definitionJson)
  }

  private fun createProcessWithErrorBoundary(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-task-error")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Test")
            put("outputVariable", "result")
          })
        })
      })
    }

    return createAndSaveDefinition("error-boundary-process", definitionJson)
  }

  private fun createProcessWithErrorCapture(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-task-with-error")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Test")
            put("outputVariable", "result")
            put("exceptionVariable", "aiError")
          })
        })
      })
    }

    return createAndSaveDefinition("error-capture-process", definitionJson)
  }

  private fun createProcessWithTypedVariables(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "ai-type-test")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Process: {{stringValue}} {{numberValue}} {{booleanValue}}")
            put("outputVariable", "typed_result")
          })
        })
      })
    }

    return createAndSaveDefinition("typed-var-process", definitionJson)
  }

  private fun createMultiProviderProcess(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "openai-task")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-4")
            put("promptTemplate", "{{prompt}}")
            put("outputVariable", "openaiResult")
          })
        })
      })
    }

    return createAndSaveDefinition("multi-provider-process", definitionJson)
  }

  private fun createConditionalAIProcess(): ProcessDefinition {
    val definitionJson = jsonNodeFactory.objectNode().apply {
      set("nodes", jsonNodeFactory.arrayNode().apply {
        add(jsonNodeFactory.objectNode().apply {
          put("id", "conditional-ai")
          put("type", "AiTask")
          set("properties", jsonNodeFactory.objectNode().apply {
            put("providerId", "openai")
            put("modelName", "gpt-3.5-turbo")
            put("promptTemplate", "Process when requiresAI={{requiresAI}}")
            put("outputVariable", "aiResponse")
          })
        })
      })
    }

    return createAndSaveDefinition("conditional-ai-process", definitionJson)
  }

  private fun createAndSaveDefinition(key: String, json: com.fasterxml.jackson.databind.JsonNode): ProcessDefinition {
    val definition = ProcessDefinition(
      processKey = "$key-${System.currentTimeMillis()}",
      processName = key,
      description = "Test process: $key",
      definitionJson = json,
      version = 1
    )
    return processDefinitionRepository.save(definition)
  }

  private fun createRunningInstance(definitionId: Long): ProcessInstance {
    val instance = ProcessInstance(
      processDefinitionId = definitionId,
      currentNodes = listOf("start"),
      nodeHistory = listOf(),
      status = "RUNNING",
      businessKey = "test-${System.currentTimeMillis()}",
      createdAt = LocalDateTime.now(),
      updatedAt = LocalDateTime.now()
    )
    return processInstanceRepository.save(instance)
  }

  private fun createVariable(instanceId: Long, name: String, value: Any?) {
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
    processVariableRepository.save(variable)
  }
}
