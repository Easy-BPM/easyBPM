package com.easy.bpm.service

import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal

class VariableMappingServiceTest : FunSpec({

    val processVariableRepository = mockk<ProcessVariableRepository>(relaxed = true)
    val objectMapper = ObjectMapper()
    val service = VariableMappingService(processVariableRepository, objectMapper)

    beforeEach {
        clearMocks(processVariableRepository)
    }

    context("applyInputMappings - Explicit Mapping Mode") {

        test("T1: Map single variable from source to target") {
            // Arrange
            val sourceVar = ProcessVariable(
                id = 1,
                processInstanceId = 100,
                name = "orderId",
                value = objectMapper.valueToTree("ORDER-123")
            )

            every {
                processVariableRepository.findByProcessInstanceId(100)
            } returns listOf(sourceVar)

            every {
                processVariableRepository.save(any())
            } returnsArgument 0

            // Act
            val stats = service.applyInputMappings(
                sourceInstanceId = 100,
                targetInstanceId = 200,
                mappings = mapOf("orderId" to "order_id"),
                propagateAll = false
            )

            // Assert
            stats.variablesMapped shouldBe 1L
            stats.successCount shouldBe 1L
            stats.skippedCount shouldBe 0L
            stats.failureCount shouldBe 0L
            stats.mode shouldBe "explicit_mapping"

            verify {
                processVariableRepository.save(match { it.processInstanceId == 200L && it.name == "order_id" })
            }
        }

        test("T2: Map multiple variables from source to target") {
            // Arrange
            val var1 = ProcessVariable(1, 100, "orderId", objectMapper.valueToTree("ORDER-123"))
            val var2 = ProcessVariable(2, 100, "customerId", objectMapper.valueToTree("CUST-456"))
            val var3 = ProcessVariable(3, 100, "amount", objectMapper.valueToTree(99.99))

            every {
                processVariableRepository.findByProcessInstanceId(100)
            } returns listOf(var1, var2, var3)

            every {
                processVariableRepository.save(any())
            } returnsArgument 0

            // Act
            val stats = service.applyInputMappings(
                sourceInstanceId = 100,
                targetInstanceId = 200,
                mappings = mapOf(
                    "orderId" to "order_id",
                    "customerId" to "customer_id",
                    "amount" to "order_amount"
                ),
                propagateAll = false
            )

            // Assert
            stats.variablesMapped shouldBe 3L
            stats.successCount shouldBe 3L
        }

        test("T3: Skip missing source variables") {
            // Arrange
            val sourceVar = ProcessVariable(1, 100, "orderId", objectMapper.valueToTree("ORDER-123"))

            every {
                processVariableRepository.findByProcessInstanceId(100)
            } returns listOf(sourceVar)

            every {
                processVariableRepository.save(any())
            } returnsArgument 0

            // Act
            val stats = service.applyInputMappings(
                sourceInstanceId = 100,
                targetInstanceId = 200,
                mappings = mapOf(
                    "orderId" to "order_id",
                    "missingVar" to "missing_target"  // This variable doesn't exist
                ),
                propagateAll = false
            )

            // Assert
            stats.variablesMapped shouldBe 1L
            stats.successCount shouldBe 1L
            stats.skippedCount shouldBe 1L
            stats.failureCount shouldBe 0L
        }

        test("T4: Handle type preservation (string)") {
            // Arrange
            val stringVar = ProcessVariable(1, 100, "name", objectMapper.valueToTree("John Doe"))
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(stringVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            service.applyInputMappings(100, 200, mapOf("name" to "customer_name"))

            // Assert
            verify {
                processVariableRepository.save(match {
                    it.name == "customer_name" && it.value.isTextual && it.value.asText() == "John Doe"
                })
            }
        }

        test("T5: Handle type preservation (number)") {
            // Arrange
            val numberVar = ProcessVariable(1, 100, "amount", objectMapper.valueToTree(99.99))
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(numberVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            service.applyInputMappings(100, 200, mapOf("amount" to "order_amount"))

            // Assert
            verify {
                processVariableRepository.save(match {
                    it.name == "order_amount" && it.value.isNumber && it.value.asDouble() == 99.99
                })
            }
        }

        test("T6: Handle type preservation (boolean)") {
            // Arrange
            val boolVar = ProcessVariable(1, 100, "isApproved", objectMapper.valueToTree(true))
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(boolVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            service.applyInputMappings(100, 200, mapOf("isApproved" to "approved"))

            // Assert
            verify {
                processVariableRepository.save(match {
                    it.name == "approved" && it.value.isBoolean && it.value.asBoolean()
                })
            }
        }

        test("T7: Handle type preservation (object/JSON)") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(mapOf("id" to "123", "total" to 99.99))
            val objectVar = ProcessVariable(1, 100, "order", orderJson)
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(objectVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            service.applyInputMappings(100, 200, mapOf("order" to "order_data"))

            // Assert
            verify {
                processVariableRepository.save(match {
                    it.name == "order_data" && it.value.isObject && it.value.get("id").asText() == "123"
                })
            }
        }
    }

    context("applyInputMappings - Propagate All Mode") {

        test("T8: Propagate all variables from source to target") {
            // Arrange
            val var1 = ProcessVariable(1, 100, "orderId", objectMapper.valueToTree("ORDER-123"))
            val var2 = ProcessVariable(2, 100, "customerId", objectMapper.valueToTree("CUST-456"))
            val var3 = ProcessVariable(3, 100, "amount", objectMapper.valueToTree(99.99))

            every {
                processVariableRepository.findByProcessInstanceId(100)
            } returns listOf(var1, var2, var3)

            every {
                processVariableRepository.save(any())
            } returnsArgument 0

            // Act
            val stats = service.applyInputMappings(
                sourceInstanceId = 100,
                targetInstanceId = 200,
                mappings = emptyMap(),  // Ignored in propagateAll mode
                propagateAll = true
            )

            // Assert
            stats.variablesMapped shouldBe 3L
            stats.successCount shouldBe 3L
            stats.mode shouldBe "propagate_all"

            verify(exactly = 3) {
                processVariableRepository.save(any())
            }
        }

        test("T9: Propagate all preserves variable names") {
            // Arrange
            val sourceVar = ProcessVariable(1, 100, "originalName", objectMapper.valueToTree("value"))
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(sourceVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            service.applyInputMappings(100, 200, emptyMap(), propagateAll = true)

            // Assert
            verify {
                processVariableRepository.save(match {
                    it.processInstanceId == 200L && it.name == "originalName"
                })
            }
        }
    }

    context("applyOutputMappings") {

        test("T10: Apply output mappings (child to parent)") {
            // Arrange
            val childVar = ProcessVariable(1, 100, "paymentStatus", objectMapper.valueToTree("APPROVED"))
            every { processVariableRepository.findByProcessInstanceId(100) } returns listOf(childVar)
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            val stats = service.applyOutputMappings(
                sourceInstanceId = 100,
                targetInstanceId = 200,
                mappings = mapOf("paymentStatus" to "status"),
                propagateAll = false
            )

            // Assert
            stats.variablesMapped shouldBe 1L
            stats.successCount shouldBe 1L
        }
    }

    context("mapVariable") {

        test("T11: Map single variable to target instance") {
            // Arrange
            val variable = ProcessVariable(1, 100, "orderId", objectMapper.valueToTree("ORDER-123"))
            every { processVariableRepository.save(any()) } returnsArgument 0

            // Act
            val result = service.mapVariable(variable, "order_id", 200)

            // Assert
            result shouldBe true
            verify {
                processVariableRepository.save(match {
                    it.name == "order_id" && it.processInstanceId == 200L
                })
            }
        }
    }

    context("getNestedProperty") {

        test("T12: Extract nested property (single level)") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(mapOf("customerId" to "CUST-123", "amount" to 99.99))
            val variable = ProcessVariable(1, 100, "order", orderJson)

            // Act
            val result = service.getNestedProperty(variable, "customerId")

            // Assert
            result shouldNotBe null
            result?.asText() shouldBe "CUST-123"
        }

        test("T13: Extract nested property (multi-level)") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(
                mapOf(
                    "customer" to mapOf(
                        "id" to "CUST-123",
                        "name" to "John Doe"
                    ),
                    "amount" to 99.99
                )
            )
            val variable = ProcessVariable(1, 100, "order", orderJson)

            // Act
            val result = service.getNestedProperty(variable, "customer.name")

            // Assert
            result shouldNotBe null
            result?.asText() shouldBe "John Doe"
        }

        test("T14: Return null for missing nested property") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(mapOf("customerId" to "CUST-123"))
            val variable = ProcessVariable(1, 100, "order", orderJson)

            // Act
            val result = service.getNestedProperty(variable, "customer.missing")

            // Assert
            result shouldBe null
        }
    }

    context("setNestedProperty") {

        test("T15: Set nested property (single level)") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(mapOf("customerId" to "CUST-123"))
            val variable = ProcessVariable(1, 100, "order", orderJson)

            // Act
            val updated = service.setNestedProperty(variable, "amount", objectMapper.valueToTree(99.99) as com.fasterxml.jackson.databind.JsonNode)

            // Assert
            updated.value.get("amount").asDouble() shouldBe 99.99
            updated.value.get("customerId").asText() shouldBe "CUST-123"
        }

        test("T16: Set nested property (multi-level, creates path)") {
            // Arrange
            val orderJson: com.fasterxml.jackson.databind.JsonNode = objectMapper.valueToTree(mapOf("id" to "ORDER-123"))
            val variable = ProcessVariable(1, 100, "order", orderJson)

            // Act
            val updated = service.setNestedProperty(
                variable,
                "customer.id",
                objectMapper.valueToTree("CUST-456") as com.fasterxml.jackson.databind.JsonNode
            )

            // Assert
            updated.value.get("customer").get("id").asText() shouldBe "CUST-456"
            updated.value.get("id").asText() shouldBe "ORDER-123"
        }
    }

    context("mergeVariables") {

        test("T17: Merge variables with target precedence") {
            // Arrange
            val sourceVar = ProcessVariable(1, 100, "key1", objectMapper.valueToTree("source_value"))
            val targetVar = ProcessVariable(2, 200, "key1", objectMapper.valueToTree("target_value"))

            // Act
            val merged = service.mergeVariables(
                mapOf("key1" to sourceVar),
                mapOf("key1" to targetVar)
            )

            // Assert
            merged["key1"]?.value?.asText() shouldBe "target_value"
        }
    }

    context("filterVariables") {

        test("T18: Filter variables by predicate") {
            // Arrange
            val var1 = ProcessVariable(1, 100, "numeric", objectMapper.valueToTree(123))
            val var2 = ProcessVariable(2, 100, "string", objectMapper.valueToTree("text"))
            val var3 = ProcessVariable(3, 100, "number2", objectMapper.valueToTree(456))

            // Act
            val filtered = service.filterVariables(
                mapOf("numeric" to var1, "string" to var2, "number2" to var3)
            ) { it.value.isNumber }

            // Assert
            filtered.size shouldBe 2
            filtered.containsKey("numeric") shouldBe true
            filtered.containsKey("number2") shouldBe true
            filtered.containsKey("string") shouldBe false
        }
    }

})

