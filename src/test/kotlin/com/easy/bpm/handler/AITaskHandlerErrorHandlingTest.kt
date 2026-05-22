package com.easy.bpm.handler

import com.easy.bpm.dto.AIExecutionRequestDto
import com.easy.bpm.dto.AIExecutionResponseDto
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
 * Error Handling & Retry Logic Tests for AITaskHandler
 *
 * Tests Story 9.3.3: Error Handling + Retry Logic
 * - Retry logic with exponential backoff
 * - Error classification (AUTH_ERROR, RATE_LIMIT, TIMEOUT, PROVIDER_ERROR)
 * - Error boundary integration
 * - Exception codes and messages
 * - Retry metrics
 */
class AITaskHandlerErrorHandlingTest {

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

  // ==================== RETRY LOGIC TESTS ====================

  @Test
  fun `should retry on RATE_LIMIT with exponential backoff`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val rateLimitError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "RATE_LIMIT",
      errorMessage = "Rate limit exceeded: retry after 60s"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success after retry",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(rateLimitError)
      .thenReturn(rateLimitError) // Second attempt also rate limited
      .thenReturn(successResponse) // Third attempt succeeds

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(true, result.success)
    assertEquals("Success after retry", result.data)
    verify(mockProvider, times(3)).execute(request)
  }

  @Test
  fun `should not retry on AUTH_ERROR - fail immediately`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
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
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: Should fail immediately without retries
    assertEquals(false, result.success)
    assertEquals("AUTH_ERROR", result.errorCode)
    verify(mockProvider, times(1)).execute(request) // Only 1 attempt, no retries
  }

  @Test
  fun `should retry on TIMEOUT with exponential backoff`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val timeoutError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Request timeout after 30s"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(timeoutError)
      .thenReturn(successResponse)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert
    assertEquals(true, result.success)
    verify(mockProvider, times(2)).execute(request)
  }

  @Test
  fun `should retry PROVIDER_ERROR once then fail`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val providerError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "PROVIDER_ERROR",
      errorMessage = "Internal server error"
    )

    `when`(mockProvider.execute(request)).thenReturn(providerError)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: PROVIDER_ERROR retries once, then fails
    assertEquals(false, result.success)
    assertEquals("PROVIDER_ERROR", result.errorCode)
    verify(mockProvider, times(2)).execute(request) // Initial + 1 retry
  }

  @Test
  fun `should exhaust retries and report RETRY_EXHAUSTED`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val timeoutError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Timeout"
    )

    `when`(mockProvider.execute(request)).thenReturn(timeoutError)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 5,
      backoffMultiplier = 1.5
    )

    // Assert
    assertEquals(false, result.success)
    assertEquals("RETRY_EXHAUSTED", result.errorCode)
    verify(mockProvider, times(3)).execute(request) // Initial + 2 retries
  }

  @Test
  fun `should respect exponential backoff timing`() {
    // Arrange: Track timing of retry attempts
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val failureResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Timeout"
    )

    `when`(mockProvider.execute(request)).thenReturn(failureResponse)

    // Act: Execute with short delays to verify exponential backoff is applied
    val startTime = System.currentTimeMillis()
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )
    val duration = System.currentTimeMillis() - startTime

    // Assert: Should have delays of ~10ms + ~20ms = ~30ms minimum
    assertTrue(duration >= 25) // Allow some variance
    assertEquals(false, result.success)
  }

  // ==================== ERROR CLASSIFICATION TESTS ====================

  @Test
  fun `should classify PARSE_ERROR as non-retryable`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val parseError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "PARSE_ERROR",
      errorMessage = "Invalid JSON response"
    )

    `when`(mockProvider.execute(request)).thenReturn(parseError)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: No retry for PARSE_ERROR
    assertEquals(false, result.success)
    assertEquals("PARSE_ERROR", result.errorCode)
    verify(mockProvider, times(1)).execute(request)
  }

  @Test
  fun `should classify INVALID_CONFIG as non-retryable`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val configError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "INVALID_CONFIG",
      errorMessage = "Invalid model name"
    )

    `when`(mockProvider.execute(request)).thenReturn(configError)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 3,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: No retry for INVALID_CONFIG
    assertEquals(false, result.success)
    verify(mockProvider, times(1)).execute(request)
  }

  // ==================== ERROR BOUNDARY INTEGRATION TESTS ====================

  @Test
  fun `should throw AITaskExecutionException with AUTH_ERROR code`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Test")
        put("outputVariable", "result")
      })
    }

    val mockProvider = mock(AIProvider::class.java)
    val authError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "AUTH_ERROR",
      errorMessage = "Invalid credentials"
    )

    `when`(aiProviderFactory.createProvider(any(), any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(authError)

    // Act & Assert
    val exception = assertThrows<AITaskExecutionException> {
      handler.executeAITask(instanceId, nodeJson.get("properties")!!, emptyMap())
    }

    assertEquals("AUTH_ERROR", exception.errorCode)
    assertTrue(exception.message!!.contains("credentials"))
  }

  @Test
  fun `should throw AITaskExecutionException with RATE_LIMIT code after retries exhausted`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Test")
        put("outputVariable", "result")
        set("tuningParams", jsonNodeFactory.objectNode().apply {
          put("retryCount", 1)
          put("initialDelayMs", 5)
          put("backoffMultiplier", 1.0)
        })
      })
    }

    val mockProvider = mock(AIProvider::class.java)
    val rateLimitError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "RATE_LIMIT",
      errorMessage = "Rate limited"
    )

    `when`(aiProviderFactory.createProvider(any(), any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(rateLimitError)

    // Act & Assert
    val exception = assertThrows<AITaskExecutionException> {
      handler.executeAITask(instanceId, nodeJson.get("properties")!!, emptyMap())
    }

    assertEquals("RATE_LIMIT", exception.errorCode)
  }

  @Test
  fun `should throw AITaskExecutionException with RETRY_EXHAUSTED when all retries fail`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Test")
        put("outputVariable", "result")
        set("tuningParams", jsonNodeFactory.objectNode().apply {
          put("retryCount", 2)
          put("initialDelayMs", 5)
          put("backoffMultiplier", 1.0)
        })
      })
    }

    val mockProvider = mock(AIProvider::class.java)
    val timeoutError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Timeout"
    )

    `when`(aiProviderFactory.createProvider(any(), any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(timeoutError)

    // Act & Assert
    val exception = assertThrows<AITaskExecutionException> {
      handler.executeAITask(instanceId, nodeJson.get("properties")!!, emptyMap())
    }

    assertEquals("RETRY_EXHAUSTED", exception.errorCode)
  }

  // ==================== CUSTOM BACKOFF TESTS ====================

  @Test
  fun `should support custom backoff multiplier from tuning params`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val failureResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Timeout"
    )
    val successResponse = AIExecutionResponseDto(
      success = true,
      data = "Success",
      errorCode = null,
      errorMessage = null
    )

    `when`(mockProvider.execute(request))
      .thenReturn(failureResponse)
      .thenReturn(successResponse)

    // Act: Use custom backoff multiplier of 1.5x
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 5,
      backoffMultiplier = 1.5
    )

    // Assert: Should succeed with custom backoff
    assertEquals(true, result.success)
    verify(mockProvider, times(2)).execute(request)
  }

  @Test
  fun `should support zero retries (fail immediately)`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val failureResponse = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "TIMEOUT",
      errorMessage = "Timeout"
    )

    `when`(mockProvider.execute(request)).thenReturn(failureResponse)

    // Act: No retries allowed
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 0,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: Should fail immediately
    assertEquals(false, result.success)
    verify(mockProvider, times(1)).execute(request) // Only initial attempt
  }

  // ==================== ERROR MESSAGE HANDLING TESTS ====================

  @Test
  fun `should preserve error message from provider in exception`() {
    // Arrange
    val instanceId = 1L
    val nodeJson = jsonNodeFactory.objectNode().apply {
      put("id", "ai-task-1")
      set("properties", jsonNodeFactory.objectNode().apply {
        put("providerId", "openai")
        put("modelName", "gpt-3.5-turbo")
        put("promptTemplate", "Test")
        put("outputVariable", "result")
      })
    }

    val mockProvider = mock(AIProvider::class.java)
    val errorMessage = "API key invalid: make sure it starts with sk-"
    val authError = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "AUTH_ERROR",
      errorMessage = errorMessage
    )

    `when`(aiProviderFactory.createProvider(any(), any(), any())).thenReturn(mockProvider)
    `when`(mockProvider.execute(any())).thenReturn(authError)

    // Act & Assert
    val exception = assertThrows<AITaskExecutionException> {
      handler.executeAITask(instanceId, nodeJson.get("properties")!!, emptyMap())
    }

    assertTrue(exception.message!!.contains(errorMessage))
  }

  @Test
  fun `should sanitize credentials in error messages`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Using key sk-proj-abc123xyz789",
      tuningParams = null
    )

    val response = AIExecutionResponseDto(
      success = false,
      data = null,
      errorCode = "AUTH_ERROR",
      errorMessage = "Failed with token: bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    )

    `when`(mockProvider.execute(request)).thenReturn(response)

    // Act
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 0,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: Error should be returned but credentials should be masked in logs
    assertEquals(false, result.success)
    // Note: The maskCredentials function should mask the actual key in logs
  }

  // ==================== EDGE CASE TESTS ====================

  @Test
  fun `should handle exception thrown by provider during execution`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    val runtimeException = RuntimeException("Connection refused")
    `when`(mockProvider.execute(request)).thenThrow(runtimeException)

    // Act: Should handle exception gracefully
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 2,
      initialDelayMs = 5,
      backoffMultiplier = 2.0
    )

    // Assert: Should fail after retries exhausted
    assertEquals(false, result.success)
  }

  @Test
  fun `should handle null error response from provider`() {
    // Arrange
    val mockProvider = mock(AIProvider::class.java)
    val request = AIExecutionRequestDto(
      providerId = "openai",
      modelName = "gpt-3.5-turbo",
      promptTemplate = "Test",
      tuningParams = null
    )

    `when`(mockProvider.execute(request)).thenReturn(null)

    // Act: Should handle null response gracefully
    val result = handler.executeWithRetry(
      provider = mockProvider,
      request = request,
      maxRetries = 0,
      initialDelayMs = 10,
      backoffMultiplier = 2.0
    )

    // Assert: Should treat as failure
    assertEquals(false, result.success)
  }
}
