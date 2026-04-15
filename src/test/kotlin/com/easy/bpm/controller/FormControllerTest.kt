package com.easy.bpm.controller

import com.easy.bpm.controller.data.DeployFormRequest
import com.easy.bpm.model.form.Form
import com.easy.bpm.service.FormService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*

class FormControllerTest : FunSpec({
    val mockFormService = mockk<FormService>()
    val objectMapper = ObjectMapper()

    val formController = FormController(mockFormService)

    beforeEach {
        clearAllMocks()
    }

    context("deployForm") {
        test("should deploy form successfully") {
            // Arrange
            val schema = objectMapper.readTree("""
                {
                    "fields": [
                        {"name": "applicantName", "type": "text"},
                        {"name": "email", "type": "email"}
                    ]
                }
            """.trimIndent())

            val request = DeployFormRequest(
                name = "ApplicationForm",
                schema = schema
            )

            val expectedForm = Form(
                id = 1,
                name = "ApplicationForm",
                schema = schema,
                version = 1
            )

            every { mockFormService.deploy("ApplicationForm", schema) } returns expectedForm

            // Act
            val result = formController.deployForm(request)

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.name shouldBe "ApplicationForm"
            result.version shouldBe 1
            verify { mockFormService.deploy("ApplicationForm", schema) }
        }
    }

    context("getLatest") {
        test("should retrieve latest form version") {
            // Arrange
            val formName = "ApplicationForm"
            val form = Form(
                id = 3,
                name = formName,
                schema = objectMapper.readTree("{}"),
                version = 3
            )

            every { mockFormService.getLatestVersionByName(formName) } returns form

            // Act
            val result = formController.getLatest(formName)

            // Assert
            result shouldNotBe null
            result?.id shouldBe 3
            result?.version shouldBe 3
        }

        test("should return null when form not found") {
            // Arrange
            val formName = "NonExistentForm"
            every { mockFormService.getLatestVersionByName(formName) } returns null

            // Act
            val result = formController.getLatest(formName)

            // Assert
            result shouldBe null
        }
    }

    context("getById") {
        test("should retrieve form by ID") {
            // Arrange
            val formId = 1L
            val form = Form(
                id = formId,
                name = "ApplicationForm",
                schema = objectMapper.readTree("{}"),
                version = 1
            )

            every { mockFormService.getById(formId) } returns form

            // Act
            val result = formController.getById(formId)

            // Assert
            result shouldNotBe null
            result?.id shouldBe formId
            result?.name shouldBe "ApplicationForm"
        }

        test("should return null when form not found") {
            // Arrange
            val formId = 999L
            every { mockFormService.getById(formId) } returns null

            // Act
            val result = formController.getById(formId)

            // Assert
            result shouldBe null
        }
    }

    context("getAllVersions") {
        test("should retrieve all versions of a form") {
            // Arrange
            val formName = "ApplicationForm"
            val forms = listOf(
                Form(id = 1, name = formName, schema = objectMapper.readTree("{}"), version = 1),
                Form(id = 2, name = formName, schema = objectMapper.readTree("{}"), version = 2),
                Form(id = 3, name = formName, schema = objectMapper.readTree("{}"), version = 3)
            )

            every { mockFormService.getAllVersionsByName(formName) } returns forms

            // Act
            val result = formController.getAllVersions(formName)

            // Assert
            result shouldHaveSize 3
            result[0].version shouldBe 1
            result[1].version shouldBe 2
            result[2].version shouldBe 3
        }

        test("should return empty list when form not found") {
            // Arrange
            val formName = "NonExistentForm"
            every { mockFormService.getAllVersionsByName(formName) } returns emptyList()

            // Act
            val result = formController.getAllVersions(formName)

            // Assert
            result.shouldBeEmpty()
        }
    }
})
