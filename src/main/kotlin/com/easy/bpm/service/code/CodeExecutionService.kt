package com.easy.bpm.service.code

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * CodeExecutionService - Reflection-based method invocation engine
 *
 * Handles:
 * - Finding methods in classes
 * - Binding parameters (with type conversion)
 * - Invoking methods
 * - Extracting return values
 * - Exception handling
 */
@Service
class CodeExecutionService(
  private val objectMapper: ObjectMapper
) {
  private val logger = LoggerFactory.getLogger(CodeExecutionService::class.java)

  /**
   * Invoke a method on a class with given parameters
   *
   * @param clazz Target class to invoke method on
   * @param method Method to invoke
   * @param params Array of parameter values (must match method signature)
   * @return Method return value (or null if void)
   * @throws IllegalAccessException if method cannot be accessed
   * @throws java.lang.reflect.InvocationTargetException if method throws exception
   */
  fun invokeMethod(
    clazz: Class<*>,
    method: Method,
    params: Array<Any?>
  ): Any? {
    return try {
      val startTime = System.currentTimeMillis()

      // Make method accessible (in case of package-private, etc.)
      method.isAccessible = true

      // Create instance if not static
      val instance = if (Modifier.isStatic(method.modifiers)) {
        null // Static method - no instance needed
      } else {
        // Try to get Kotlin object INSTANCE for singletons
        try {
          val instanceField = clazz.getDeclaredField("INSTANCE")
          instanceField.isAccessible = true
          instanceField.get(null) // Get the static INSTANCE field
        } catch (ex: NoSuchFieldException) {
          // Not a Kotlin object, create a regular instance
          clazz.getDeclaredConstructor().newInstance()
        }
      }

      // Invoke method
      val result = method.invoke(instance, *params)

      val duration = System.currentTimeMillis() - startTime
      logger.debug(
        "Method invocation successful: {}.{}() took {}ms",
        clazz.simpleName,
        method.name,
        duration
      )

      result
    } catch (ex: Exception) {
      logger.error(
        "Error invoking method: {}.{}()",
        clazz.simpleName,
        method.name,
        ex
      )
      throw ex
    }
  }

  /**
   * Convert a parameter value to the target type
   *
   * Handles:
   * - Null values
   * - Primitive types (int, long, double, boolean)
   * - Wrapper types (Integer, Long, Double, Boolean)
   * - String conversion
   * - JSON deserialization for complex types
   *
   * @param value Source value
   * @param targetType Target parameter type
   * @return Converted value, or null if source is null
   */
  fun convertParameter(value: Any?, targetType: Class<*>): Any? {
    if (value == null) {
      return if (targetType.isPrimitive) {
        // Null not allowed for primitives - throw error
        throw IllegalArgumentException("Cannot assign null to primitive type: $targetType")
      } else {
        null
      }
    }

    // If types already match, return as-is
    if (targetType.isAssignableFrom(value.javaClass)) {
      return value
    }

    return when {
      // String conversions
      targetType == String::class.java -> value.toString()

      // Int conversions
      targetType == Int::class.java || targetType == Int::class.javaPrimitiveType ->
        when (value) {
          is Number -> value.toInt()
          is String -> value.toIntOrNull() ?: throw IllegalArgumentException("Cannot convert '$value' to Int")
          else -> throw IllegalArgumentException("Cannot convert ${value.javaClass.simpleName} to Int")
        }

      // Long conversions
      targetType == Long::class.java || targetType == Long::class.javaPrimitiveType ->
        when (value) {
          is Number -> value.toLong()
          is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Cannot convert '$value' to Long")
          else -> throw IllegalArgumentException("Cannot convert ${value.javaClass.simpleName} to Long")
        }

      // Double conversions
      targetType == Double::class.java || targetType == Double::class.javaPrimitiveType ->
        when (value) {
          is Number -> value.toDouble()
          is String -> value.toDoubleOrNull() ?: throw IllegalArgumentException("Cannot convert '$value' to Double")
          else -> throw IllegalArgumentException("Cannot convert ${value.javaClass.simpleName} to Double")
        }

      // Boolean conversions
      targetType == Boolean::class.java || targetType == Boolean::class.javaPrimitiveType ->
        when (value) {
          is Boolean -> value
          is String -> value.toBoolean()
          is Number -> value.toInt() != 0
          else -> throw IllegalArgumentException("Cannot convert ${value.javaClass.simpleName} to Boolean")
        }

      // JSON deserialization for complex types
      else -> {
        logger.debug("Converting to complex type: {}", targetType.simpleName)
        try {
          objectMapper.convertValue(value, targetType)
        } catch (ex: Exception) {
          logger.error("Failed to convert to {}: {}", targetType.simpleName, ex.message)
          throw IllegalArgumentException("Cannot convert to ${targetType.simpleName}: ${ex.message}")
        }
      }
    }
  }

  /**
   * Extract a value from method result using dot notation
   *
   * Examples:
   * - "total" -> result.total
   * - "order.amount" -> result.order.amount
   * - "items[0].price" -> result.items[0].price
   *
   * @param result Object returned from method invocation
   * @param path Path to extract (null = return entire result)
   * @return Extracted value
   */
  fun extractReturnValue(result: Any?, path: String? = null): Any? {
    if (result == null || path == null) {
      return result
    }

    return try {
      val jsonNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(result)
      var current: com.fasterxml.jackson.databind.JsonNode? = jsonNode

      path.split(".").forEach { part ->
        if (current == null || current!!.isNull) {
          return@forEach
        }

        current = when {
          part.contains("[") && part.contains("]") -> {
            // Handle array access: items[0]
            val fieldName = part.substring(0, part.indexOf("["))
            val indexStr = part.substring(part.indexOf("[") + 1, part.indexOf("]"))
            val index = indexStr.toInt()
            val field = current!![fieldName]
            if (field != null && field.isArray) field[index] else null
          }
          else -> current!![part]
        }
      }

      if (current != null && !current!!.isNull) {
        objectMapper.treeToValue(current, Any::class.java)
      } else {
        null
      }
    } catch (ex: Exception) {
      logger.error("Failed to extract value from result using path: {}", path, ex)
      throw IllegalArgumentException("Cannot extract '$path' from result: ${ex.message}")
    }
  }

  /**
   * Find a method in a class by name
   *
   * @param clazz Class to search
   * @param methodName Method name to find
   * @return Found method, or null if not found
   */
  fun findMethod(clazz: Class<*>, methodName: String): Method? {
    return clazz.declaredMethods.find { it.name == methodName }
  }

  /**
   * Get method signature as string
   *
   * Example: "calculateTotal(Order, double) -> OrderTotal"
   */
  fun getMethodSignature(method: Method): String {
    val params = method.parameterTypes.joinToString(", ") { it.simpleName }
    val returnType = method.returnType.simpleName
    return "${method.name}($params) -> $returnType"
  }
}

