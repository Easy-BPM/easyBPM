package com.easy.bpm.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit Tests for CodeExecutionService
 *
 * Tests reflection-based method invocation, parameter binding, and type conversion
 */
class CodeExecutionServiceTest {

  private val objectMapper = ObjectMapper()
  private val service = CodeExecutionService(objectMapper)

  // ==================== TYPE CONVERSION TESTS ====================

  @Test
  fun `should convert String to Int`() {
    val result = service.convertParameter("42", Int::class.java)
    assertEquals(42, result)
  }

  @Test
  fun `should convert String to Long`() {
    val result = service.convertParameter("9999", Long::class.java)
    assertEquals(9999L, result)
  }

  @Test
  fun `should convert String to Double`() {
    val result = service.convertParameter("3.14", Double::class.java)
    assertEquals(3.14, result as Double, 0.01)
  }

  @Test
  fun `should convert String to Boolean`() {
    assertEquals(true, service.convertParameter("true", Boolean::class.java))
    assertEquals(false, service.convertParameter("false", Boolean::class.java))
  }

  @Test
  fun `should convert Number to different numeric types`() {
    val num = 42

    val toInt = service.convertParameter(num, Int::class.java)
    assertEquals(42, toInt)

    val toLong = service.convertParameter(num, Long::class.java)
    assertEquals(42L, toLong)

    val toDouble = service.convertParameter(num, Double::class.java)
    assertEquals(42.0, toDouble)
  }

  @Test
  fun `should handle null values for non-primitive types`() {
    val result = service.convertParameter(null, String::class.java)
    assertNull(result)
  }

  @Test
  fun `should throw exception when null assigned to primitive type`() {
    assertThrows<IllegalArgumentException> {
      service.convertParameter(null, Int::class.java)
    }
  }

  @Test
  fun `should throw exception for invalid type conversion`() {
    assertThrows<IllegalArgumentException> {
      service.convertParameter("not-a-number", Int::class.java)
    }
  }

  // ==================== METHOD INVOCATION TESTS ====================

  @Test
  fun `should invoke simple static method`() {
    val clazz = TestCalculator::class.java
    val method = service.findMethod(clazz, "add") ?: throw AssertionError("Method not found")

    val result = service.invokeMethod(clazz, method, arrayOf(5, 3))
    assertEquals(8, result)
  }

  @Test
  fun `should invoke method with String parameter`() {
    val clazz = TestStringUtils::class.java
    val method = service.findMethod(clazz, "toUpperCase") 
      ?: throw AssertionError("Method not found")

    val result = service.invokeMethod(clazz, method, arrayOf("hello"))
    assertEquals("HELLO", result)
  }

  @Test
  fun `should invoke method that returns null`() {
    val clazz = TestNullReturner::class.java
    val method = service.findMethod(clazz, "returnNull")
      ?: throw AssertionError("Method not found")

    val result = service.invokeMethod(clazz, method, arrayOf())
    assertNull(result)
  }

  @Test
  fun `should invoke instance method (non-static)`() {
    val clazz = TestMutableCounter::class.java
    val method = service.findMethod(clazz, "increment")
      ?: throw AssertionError("Method not found")

    // Service should create instance automatically
    val result = service.invokeMethod(clazz, method, arrayOf())
    assertEquals(1, result)
  }

  // ==================== METHOD SIGNATURE TESTS ====================

  @Test
  fun `should generate correct method signature`() {
    val clazz = TestCalculator::class.java
    val method = service.findMethod(clazz, "add")
      ?: throw AssertionError("Method not found")

    val signature = service.getMethodSignature(method)
    // Expected format: "add(int, int) -> int"
    assert(signature.contains("add"))
    assert(signature.contains("int"))
  }

  // ==================== RETURN VALUE EXTRACTION TESTS ====================

  @Test
  fun `should extract simple return value without path`() {
    val result = mapOf("total" to 100, "tax" to 8)
    val extracted = service.extractReturnValue(result, null)
    assertEquals(result, extracted)
  }

  @Test
  fun `should handle null result`() {
    val extracted = service.extractReturnValue(null, "anyPath")
    assertNull(extracted)
  }
}

// ==================== TEST FIXTURES ====================

/**
 * Simple calculator for testing method invocation
 */
object TestCalculator {
  fun add(a: Int, b: Int): Int = a + b

  fun multiply(a: Int, b: Int): Int = a * b

  fun divide(a: Int, b: Int): Int {
    if (b == 0) throw IllegalArgumentException("Division by zero")
    return a / b
  }
}

/**
 * String utilities for testing string parameters
 */
object TestStringUtils {
  fun toUpperCase(text: String): String = text.uppercase()

  fun toLowerCase(text: String): String = text.lowercase()

  fun length(text: String): Int = text.length

  fun concatenate(a: String, b: String): String = a + b
}

/**
 * Tests null return handling
 */
object TestNullReturner {
  fun returnNull(): String? = null

  fun returnValue(): String = "value"
}

/**
 * Tests instance method invocation
 */
class TestMutableCounter {
  private var count = 0

  fun increment(): Int {
    count++
    return count
  }

  fun decrement(): Int {
    count--
    return count
  }

  fun getCount(): Int = count
}

/**
 * Tests complex object handling
 */data class OrderData(
  val orderId: String,
  val amount: Double,
  val items: List<String>
)

data class OrderResult(
  val orderId: String,
  val subtotal: Double,
  val tax: Double,
  val total: Double
)

object TestOrderProcessor {
  fun calculateOrderTotal(order: OrderData, taxRate: Double): OrderResult {
    val subtotal = order.amount
    val tax = subtotal * taxRate
    val total = subtotal + tax

    return OrderResult(
      orderId = order.orderId,
      subtotal = subtotal,
      tax = tax,
      total = total
    )
  }
}

