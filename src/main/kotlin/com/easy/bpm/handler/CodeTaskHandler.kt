package com.easy.bpm.handler

import com.easy.bpm.entity.CodeTaskExecutionAudit
import com.easy.bpm.repository.CodeTaskExecutionAuditRepository
import com.easy.bpm.repository.CodeTaskJarRepository
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.service.code.CodeClassDiscoveryService
import com.easy.bpm.service.code.CodeExecutionService
import com.easy.bpm.service.incident.IncidentService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * CodeTaskHandler - Orchestrates Code Task execution lifecycle
 *
 * Full workflow:
 * 1. Load JAR from blob storage
 * 2. Create isolated ClassLoader
 * 3. Find target class and method via reflection
 * 4. Apply input variable mappings (process vars → method params)
 * 5. Invoke method with timeout protection
 * 6. Capture return value
 * 7. Apply output variable mappings (return → process vars)
 * 8. Record execution in audit trail
 * 9. Return result
 *
 * Error handling: Captures exceptions as variables for error boundaries
 */
@Service
class CodeTaskHandler(
  private val codeTaskJarRepository: CodeTaskJarRepository,
  private val codeClassDiscoveryService: CodeClassDiscoveryService,
  private val codeExecutionService: CodeExecutionService,
  private val codeTaskExecutionAuditRepository: CodeTaskExecutionAuditRepository,
  private val incidentService: IncidentService,
  private val objectMapper: ObjectMapper
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  /**
   * Execute a Code Task within a process instance
   *
   * @param instanceId Process instance ID
   * @param nodeId Code Task node ID
   * @param jarId JAR file ID
   * @param className Fully qualified class name
   * @param methodName Method name to invoke
   * @param inputMappings Map of: processVarName -> methodParamName
   * @param outputMappings Map of: returnValuePath -> processVarName
   * @param inputVariables Current process variables
   * @return Map of output variables to set in process
   * @throws CodeTaskExecutionException on any error
   */
  @Transactional
  fun executeCodeTask(
    instanceId: Long,
    nodeId: String,
    jarId: Long,
    className: String,
    methodName: String,
    inputMappings: Map<String, String>,
    outputMappings: Map<String, String>,
    inputVariables: Map<String, Any?>
  ): Map<String, Any?> {
    val executionStart = LocalDateTime.now()
    val startTime = System.currentTimeMillis()

    try {
      logger.info(
        "Starting Code Task execution: instance={}, node={}, class={}, method={}",
        instanceId, nodeId, className, methodName
      )

      // Step 1: Load JAR
      val jar = codeTaskJarRepository.findById(jarId).orElseThrow {
        CodeTaskExecutionException("JAR file not found: jarId=$jarId")
      }

      // Step 2: Create ClassLoader
      val classLoader = codeClassDiscoveryService.createClassLoader(jar.content)

      // Step 3: Load class and find method
      val clazz = try {
        classLoader.loadClass(className)
      } catch (ex: ClassNotFoundException) {
        throw CodeTaskExecutionException("Class not found: $className in JAR ${jar.fileName}")
      }

      val method = codeExecutionService.findMethod(clazz, methodName)
        ?: throw CodeTaskExecutionException("Method not found: $className.$methodName")

      // Step 4: Apply input mappings
      val methodParams = applyInputMappings(method, inputMappings, inputVariables)

      // Step 5: Invoke method
      logger.debug("Invoking method: {}.{}() with {} parameters", className, methodName, methodParams.size)
      val result = codeExecutionService.invokeMethod(clazz, method, methodParams)

      // Step 6: Apply output mappings
      val outputVars = applyOutputMappings(result, outputMappings)

      // Step 7: Record successful execution
      val executionTimeMs = (System.currentTimeMillis() - startTime).toInt()
      recordExecution(
        instanceId = instanceId,
        nodeId = nodeId,
        jarId = jarId,
        className = className,
        methodName = methodName,
        inputVariables = inputVariables,
        outputVariables = outputVars,
        executionTimeMs = executionTimeMs,
        status = CodeTaskExecutionAudit.STATUS_COMPLETED,
        errorMessage = null
      )

      logger.info(
        "Code Task completed successfully: instance={}, method={}(), time={}ms",
        instanceId, methodName, executionTimeMs
      )

      return outputVars

    } catch (ex: CodeTaskExecutionException) {
      // Execution error - record failure
      val executionTimeMs = (System.currentTimeMillis() - startTime).toInt()

      recordExecution(
        instanceId = instanceId,
        nodeId = nodeId,
        jarId = jarId,
        className = className,
        methodName = methodName,
        inputVariables = inputVariables,
        outputVariables = emptyMap(),
        executionTimeMs = executionTimeMs,
        status = CodeTaskExecutionAudit.STATUS_FAILED,
        errorMessage = ex.message
      )

      incidentService.createIncident(
        processInstanceId = instanceId,
        nodeId = nodeId,
        source = IncidentSource.CODE_TASK,
        message = ex.message ?: "Code task execution failed",
        technicalDetails = "Code task failed in $className.$methodName",
        externalReferenceId = "code_task:$className.$methodName"
      )

      logger.error("Code Task execution failed: instance={}", instanceId, ex)
      // Re-throw for process error handling
      throw ex
    } catch (ex: Exception) {
      // Unexpected error
      val executionTimeMs = (System.currentTimeMillis() - startTime).toInt()

      recordExecution(
        instanceId = instanceId,
        nodeId = nodeId,
        jarId = jarId,
        className = className,
        methodName = methodName,
        inputVariables = inputVariables,
        outputVariables = emptyMap(),
        executionTimeMs = executionTimeMs,
        status = CodeTaskExecutionAudit.STATUS_FAILED,
        errorMessage = "Unexpected error: ${ex.message}"
      )

      incidentService.createIncident(
        processInstanceId = instanceId,
        nodeId = nodeId,
        source = IncidentSource.CODE_TASK,
        message = "Code task execution failed: ${ex.message}",
        technicalDetails = "Unexpected code task failure in $className.$methodName",
        externalReferenceId = "code_task:$className.$methodName"
      )

      logger.error("Code Task failed with unexpected error: instance={}", instanceId, ex)
      throw CodeTaskExecutionException("Code Task execution failed: ${ex.message}", ex)
    }
  }

  /**
   * Apply input variable mappings
   *
   * Maps process variables to method parameters using the provided mappings.
   *
   * @param method Target method (used to determine parameter types)
   * @param mappings: { processVarName -> methodParamIndex }
   * @param inputVariables Available process variables
   * @return Array of parameter values for method invocation
   */
  private fun applyInputMappings(
    method: java.lang.reflect.Method,
    mappings: Map<String, String>,
    inputVariables: Map<String, Any?>
  ): Array<Any?> {
    val parameterTypes = method.parameterTypes
    val params = Array<Any?>(parameterTypes.size) { null }

    mappings.forEach { (processVar, methodParamIndex) ->
      val paramIndex = methodParamIndex.toIntOrNull()
        ?: throw CodeTaskExecutionException("Invalid parameter index: $methodParamIndex")

      if (paramIndex < 0 || paramIndex >= parameterTypes.size) {
        throw CodeTaskExecutionException(
          "Parameter index out of range: $paramIndex (method has ${parameterTypes.size} parameters)"
        )
      }

      val value = inputVariables[processVar]
      val paramType = parameterTypes[paramIndex]

      params[paramIndex] = try {
        codeExecutionService.convertParameter(value, paramType)
      } catch (ex: Exception) {
        throw CodeTaskExecutionException(
          "Failed to convert input variable '$processVar' to parameter type ${paramType.simpleName}: ${ex.message}"
        )
      }
    }

    return params
  }

  /**
   * Apply output variable mappings
   *
   * Maps method return value to process variables.
   *
   * @param result Method return value
   * @param mappings: { returnValuePath -> processVarName }
   * @return Map of variables to set in process: { processVarName -> value }
   */
  private fun applyOutputMappings(
    result: Any?,
    mappings: Map<String, String>
  ): Map<String, Any?> {
    val outputVars = mutableMapOf<String, Any?>()

    mappings.forEach { (returnPath, processVar) ->
      try {
        val value = codeExecutionService.extractReturnValue(result, returnPath)
        outputVars[processVar] = value
      } catch (ex: Exception) {
        throw CodeTaskExecutionException(
          "Failed to extract return value path '$returnPath': ${ex.message}"
        )
      }
    }

    return outputVars
  }

  /**
   * Record execution in audit trail
   *
   * @param instanceId Process instance ID
   * @param nodeId Code Task node ID
   * @param jarId JAR file ID
   * @param className Fully qualified class name
   * @param methodName Method name
   * @param inputVariables Input variable snapshots
   * @param outputVariables Output variable snapshots
   * @param executionTimeMs Execution duration in milliseconds
   * @param status Execution status (COMPLETED, FAILED, TIMEOUT)
   * @param errorMessage Error message (if failed)
   */
  @Transactional
  private fun recordExecution(
    instanceId: Long,
    nodeId: String,
    jarId: Long?,
    className: String,
    methodName: String,
    inputVariables: Map<String, Any?>,
    outputVariables: Map<String, Any?>,
    executionTimeMs: Int,
    status: String,
    errorMessage: String?
  ) {
    try {
      val audit = CodeTaskExecutionAudit(
        instanceId = instanceId,
        nodeId = nodeId,
        jarId = jarId,
        className = className,
        methodName = methodName,
        inputVariables = objectMapper.writeValueAsString(inputVariables),
        outputVariables = objectMapper.writeValueAsString(outputVariables),
        executionTimeMs = executionTimeMs,
        status = status,
        errorMessage = errorMessage,
        executedAt = LocalDateTime.now()
      )

      codeTaskExecutionAuditRepository.save(audit)
      logger.debug("Recorded Code Task execution audit: id={}", audit.id)
    } catch (ex: Exception) {
      logger.error("Failed to record execution audit for instance {}", instanceId, ex)
      // Don't fail the entire execution if audit fails
    }
  }
}

/**
 * Exception thrown during Code Task execution
 */
class CodeTaskExecutionException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause)

