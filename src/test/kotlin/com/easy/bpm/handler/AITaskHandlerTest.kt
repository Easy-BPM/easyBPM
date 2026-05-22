package com.easy.bpm.handler

import com.easy.bpm.dto.AIExecutionRequestDto
import com.easy.bpm.dto.AIExecutionResponseDto
import com.easy.bpm.entity.ProcessVariable
import com.easy.bpm.exception.AITaskExecutionException
import com.easy.bpm.provider.AIProvider
import com.easy.bpm.service.AIProviderFactory
import com.easy.bpm.service.CredentialVault
import com.easy.bpm.repository.ProcessVariableRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit Tests for AITaskHandler
 *
 * Tests complete AI task execution lifecycle:
 * - Configuration extraction from node properties
 * - Variable substitution ({{variableName}} → actual values)
 * - Provider invocation
 * - Retry logic with exponential backoff
 * - Error classification and mapping
 * - Response binding to process variables
 * - Credential masking in logs
 */
class AITaskHandlerTest {

  private lateinit var handler: AITaskHandler
  private lateinit var aiProviderFactory: AIProviderFactory
  private lateinit var credentialVault: CredentialVault
  private lateinit var processVariableRepository: ProcessVariableRepository
  private val objectMapper = ObjectMapper()
  private val jsonNodeFactory = JsonNodeFactory.instance

  @BeforeEach
  fun setup() {
    aiProviderFactory = mock(AIProviderFactory::class.java)
    credentialVault = mock(CredentialVault::class.java)
    processVariableRepository = mock(ProcessVariableRepository::class.java)

    handler = AITaskHandler(
      aiProviderFactory = aiProviderFactory,
      credentialVault = credentialVault,
      processVariableRepository = processVariableRepository,
      objectMapper = objectMapper
    )
  }

  // ==================== HAPPY PATH TESTS ====================

  @Test
  fun `should execute AI task successfully with default model`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Summarize: {{text}}")
        put("outputVariable", "summary")
      })
    }
    val inputVariables = mapOf(
      "text" to "This is a long document that needs summarization."
    )

    val mockProvider = mock(AIProvider::class.java)
    val response = AIExecutionResponseDto(
      success = true,
      data = "This is a summary.",
      errorCode = null,
      errorMessage = null
    )

    `when`(aiProviderFactory.createProvider("openai", any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(response)

    // Act
    val result = handler.executeAITask(instanceId, nodeJson.get("properties")!!, inputVariables)

    // Assert
    assertNotNull(result)
    assertEquals("This is a summary.", result["summary"])
    verify(mockProvider, times(1)).execute(any())
  }

  @Test
  fun `should substitute single variable in prompt template`() {
    // Arrange
    val template = "Analyze customer {{customerId}}"
    val variables = mapOf("customerId" to "CUST-123")

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Analyze customer CUST-123", result)
  }

  @Test
  fun `should substitute multiple variables in prompt template`() {
    // Arrange
    val template = "Customer {{customerId}} ordered {{quantity}} items worth {{total}} dollars"
    val variables = mapOf(
      "customerId" to "CUST-456",
      "quantity" to 5,
      "total" to 299.99
    )

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Customer CUST-456 ordered 5 items worth 299.99 dollars", result)
  }

  @Test
  fun `should handle missing variable in template`() {
    // Arrange
    val template = "Process {{orderId}} with status {{status}}"
    val variables = mapOf("orderId" to "ORD-789")
    // Note: 'status' is missing

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    // Missing variable should remain as {{status}}
    assertEquals("Process ORD-789 with status {{status}}", result)
  }

  @Test
  fun `should convert number to string in variable substitution`() {
    // Arrange
    val template = "Total amount: {{amount}}"
    val variables = mapOf("amount" to 1234.56)

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Total amount: 1234.56", result)
  }

  @Test
  fun `should convert boolean to string in variable substitution`() {
    // Arrange
    val template = "Is premium: {{isPremium}}"
    val variables = mapOf("isPremium" to true)

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Is premium: true", result)
  }

  @Test
  fun `should serialize JSON object in variable substitution`() {
    // Arrange
    val template = "User data: {{userData}}"
    val userData = mapOf("name" to "John", "age" to 30)
    val variables = mapOf("userData" to userData)

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertTrue(result.contains("\"name\""))
    assertTrue(result.contains("\"John\""))
  }

  @Test
  fun `should extract tuning parameters with defaults`() {
    // Arrange
    val tuningJson = jsonNodeFactory.objectNode().apply {
      put("temperature", 0.5)
      // topP not provided - should use default
    }

    // Act
    val result = handler.extractTuningParams(tuningJson)

    // Assert
    assertNotNull(result)
    assertEquals(0.5, result.temperature, 0.001)
    assertEquals(1.0, result.topP, 0.001) // default
  }

  @Test
  fun `should handle null tuning parameters`() {
    // Act
    val result = handler.extractTuningParams(null)

    // Assert
    assertNotNull(result)
    assertEquals(0.7, result.temperature, 0.001) // default
    assertEquals(1.0, result.topP, 0.001) // default
  }

  // ==================== RETRY LOGIC TESTS ====================

  @Test
  fun `should retry on rate limit error with backoff`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      prompt = "Test prompt",
      tuningParams = null
    )

    // First call: RATE_LIMIT error, Second call: success
    val rateLimitResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "RATE_LIMIT",
      errorMessage = "Rate limit exceeded"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(rateLimitResponse)
      .thenReturn(successResponse)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 1,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(true, result.success)
    assertEquals("Success", result.data)
    verify(mockProvider, times(2)).execute(request)
  }

  @Test
  fun `should not retry on auth error`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      prompt = "Test prompt",
      tuningParams = null
    )

    val authError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "AUTH_ERROR",
      errorMessage = "Invalid API key"
    )

    `when`(mockProvider.execute(request)).thenReturn(authError)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 1,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(false, result.success)
    assertEquals("AUTH_ERROR", result.errorCode)
    verify(mockProvider, times(1)).execute(request) // Only called once, no retries
  }

  @Test
  fun `should retry on timeout error`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      prompt = "Test prompt",
      tuningParams = null
    )

    val timeoutResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Request timeout"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(timeoutResponse)
      .thenReturn(successResponse)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 1,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(true, result.success)
    verify(mockProvider, times(2)).execute(request)
  }

  @Test
  fun `should exhaust retries and fail`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      prompt = "Test prompt",
      tuningParams = null
    )

    val timeoutResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Request timeout"
    )

    `when`(mockProvider.execute(request)).thenReturn(timeoutResponse)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 1,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(false, result.success)
    assertEquals("RETRY_EXHAUSTED", result.errorCode)
    verify(mockProvider, times(3)).execute(request) // Initial + 2 retries
  }

  // ==================== ERROR HANDLING TESTS ====================

  @Test
  fun `should throw AITaskExecutionException with proper error code`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Test")
        put("outputVariable", "response")
      })
    }
    val inputVariables = mapOf("var1" to "value1")

    val mockProvider = mock(AIProvider::class.java)
    val response = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "AUTH_ERROR",
      errorMessage = "Invalid credentials"
    )

    `when`(aiProviderFactory.createProvider(any(), any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(response)

    // Act & Assert
    val exception = assertThrows<AITaskExecutionException> {
      handler.executeAITask(instanceId, nodeJson.get("properties")!!, inputVariables)
    }

    assertEquals("AUTH_ERROR", exception.errorCode)
  }

  @Test
  fun `should mask API credentials in logs`() {
    // Arrange
    val textWithCredentials = """
      Using API key: sk-proj-abc123xyz789
      Bearer token: bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
      token: mytoken123456789
    """.trimIndent()

    // Act
    val masked = handler.maskCredentials(textWithCredentials)

    // Assert
    assertTrue(masked.contains("sk-***"))
    assertTrue(masked.contains("bearer ***"))
    assertTrue(masked.contains("token=***"))
    assertTrue(!masked.contains("abc123xyz789"))
    assertTrue(!masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
  }

  @Test
  fun `should properly mask credential reference names`() {
    // Arrange
    val text = "credential_id=openai_api_key_secret_12345"

    // Act
    val masked = handler.maskCredentials(text)

    // Assert
    assertTrue(masked.contains("***") || !masked.contains("secret"))
  }

  // ==================== CONFIGURATION TESTS ====================

  @Test
  fun `should extract provider config from node properties`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "anthropic")
        put("modelName", "claude-3-opus")
        put("credentialRef", "ANTHROPIC_KEY")
        put("promptTemplate", "Process {{data}}")
        put("outputVariable", "result")
        set("tuningParams", jsonNodeFactory.objectNode().apply {
          put("temperature", 0.8)
          put("maxTokens", 4096)
        })
      })
    }
    val inputVariables = mapOf("data" to "test")

    val mockProvider = mock(AIProvider::class.java)
    val response = AIExecutionResponseDto(
      success = true,
      data = "Processed",
      errorCode = null,
      errorMessage = null
    )

    `when`(aiProviderFactory.createProvider("anthropic", any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(response)

    // Act
    val result = handler.executeAITask(instanceId, nodeJson.get("properties")!!, inputVariables)

    // Assert
    assertNotNull(result)
    verify(aiProviderFactory, times(1)).createProvider(
      eq("anthropic"),
      any(),
      any()
    )
  }

  // ==================== EDGE CASE TESTS ====================

  @Test
  fun `should handle empty variable map`() {
    // Arrange
    val template = "No variables here"
    val variables = emptyMap<String, Any?>()

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("No variables here", result)
  }

  @Test
  fun `should handle template with no variables`() {
    // Arrange
    val template = "Static prompt with no substitutions"
    val variables = mapOf("unused" to "value")

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Static prompt with no substitutions", result)
  }

  @Test
  fun `should handle malformed variable references gracefully`() {
    // Arrange
    val template = "Text with {{ incomplete brace and {{valid}} var"
    val variables = mapOf("valid" to "value")

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    // Only valid {{variable}} should be replaced
    assertEquals("Text with {{ incomplete brace and value var", result)
  }

  @Test
  fun `should handle null variable values`() {
    // Arrange
    val template = "Value is {{nullable}}"
    val variables = mapOf<String, Any?>("nullable" to null)

    // Act
    val result = handler.substituteVariables(template, variables)

    // Assert
    assertEquals("Value is null", result)
  }

  @Test
  fun `should support custom tuning parameters for retry backoff`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      prompt = "Test",
      tuningParams = null
    )

    val failResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "RATE_LIMIT",
      errorMessage = "Rate limited"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(failResponse)
      .thenReturn(successResponse)

    // Act - Custom backoff multiplier
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 10,
      backoffMultiplier = 1.5
    )

    // Assert
    assertEquals(true, result.success)
    verify(mockProvider, times(2)).execute(request)
  }
}
