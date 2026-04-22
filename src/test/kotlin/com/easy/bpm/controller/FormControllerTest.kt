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
                formId = "applicationForm",
                name = "ApplicationForm",
                schema = schema
            )

            val expectedForm = Form(
                id = 1,
                formId = "applicationForm",
                name = "ApplicationForm",
                schema = schema,
                version = 1
            )

            every { mockFormService.deploy("applicationForm", "ApplicationForm", schema) } returns expectedForm

            // Act
            val result = formController.deployForm(request)

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.formId shouldBe "applicationForm"
            result.name shouldBe "ApplicationForm"
            result.version shouldBe 1
            verify { mockFormService.deploy("applicationForm", "ApplicationForm", schema) }
        }
    }

    context("getLatest") {
        test("should retrieve latest form version by formId") {
            // Arrange
            val formId = "applicationForm"
            val form = Form(
                id = 3,
                formId = formId,
                name = "ApplicationForm",
                schema = objectMapper.readTree("{}"),
                version = 3
            )

            every { mockFormService.getLatestVersionByFormId(formId) } returns form

            // Act
            val result = formController.getLatest(formId, null)

            // Assert
            result shouldNotBe null
            result?.id shouldBe 3
            result?.formId shouldBe formId
            result?.version shouldBe 3
        }

        test("should support lookup by name") {
            // Arrange
            val formName = "ApplicationForm"
            val form = Form(
                id = 2,
                formId = "applicationForm",
                name = formName,
                schema = objectMapper.readTree("{}"),
                version = 2
            )
            every { mockFormService.getLatestVersionByName(formName) } returns form

            // Act
            val result = formController.getLatest(null, formName)

            // Assert
            result shouldNotBe null
            result?.name shouldBe formName
        }
    }

    context("getById") {
        test("should retrieve form by ID") {
            // Arrange
            val formId = 1L
            val form = Form(
                id = formId,
                formId = "applicationForm",
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
        test("should retrieve all versions of a form by formId") {
            // Arrange
            val formId = "applicationForm"
            val forms = listOf(
                Form(id = 1, formId = formId, name = "ApplicationForm", schema = objectMapper.readTree("{}"), version = 1),
                Form(id = 2, formId = formId, name = "ApplicationForm", schema = objectMapper.readTree("{}"), version = 2),
                Form(id = 3, formId = formId, name = "ApplicationForm", schema = objectMapper.readTree("{}"), version = 3)
            )

            every { mockFormService.getAllVersionsByFormId(formId) } returns forms

            // Act
            val result = formController.getAllVersions(formId, null)

            // Assert
            result shouldHaveSize 3
            result[0].version shouldBe 1
            result[1].version shouldBe 2
            result[2].version shouldBe 3
        }

        test("should support version lookup by name") {
            // Arrange
            val formName = "ApplicationForm"
            val forms = listOf(
                Form(id = 1, formId = "applicationForm", name = formName, schema = objectMapper.readTree("{}"), version = 1)
            )
            every { mockFormService.getAllVersionsByName(formName) } returns forms

            // Act
            val result = formController.getAllVersions(null, formName)

            // Assert
            result shouldHaveSize 1
        }
    }
})

