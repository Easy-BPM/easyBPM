package com.easy.bpm.service

import com.easy.bpm.model.form.Form
import com.easy.bpm.repository.form.FormDefinitionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import java.util.*

class FormServiceTest : FunSpec({
    val mockFormRepository = mockk<FormDefinitionRepository>()
    val formService = FormService(mockFormRepository)
    val objectMapper = ObjectMapper()

    beforeEach {
        clearAllMocks()
    }

    context("deploy") {
        test("should deploy new form successfully") {
            // Arrange
            val formId = "applicationForm"
            val formName = "ApplicationForm"
            val schema = objectMapper.readTree("""
                {
                    "fields": [
                        {"name": "applicantName", "type": "text"},
                        {"name": "email", "type": "email"}
                    ]
                }
            """.trimIndent())

            every { mockFormRepository.findTopByFormIdOrderByVersionDesc(formId) } returns null
            val expectedForm = Form(
                id = 1,
                formId = formId,
                name = formName,
                schema = schema,
                version = 1
            )
            every { mockFormRepository.save(any()) } returns expectedForm

            // Act
            val result = formService.deploy(formId, formName, schema)

            // Assert
            result shouldNotBe null
            result.id shouldBe 1
            result.formId shouldBe formId
            result.name shouldBe formName
            result.version shouldBe 1
            verify { mockFormRepository.save(any()) }
        }

        test("should increment version for existing form") {
            // Arrange
            val formId = "applicationForm"
            val formName = "ApplicationForm"
            val existingForm = Form(
                id = 1,
                formId = formId,
                name = formName,
                schema = objectMapper.readTree("{}"),
                version = 2
            )
            val schema = objectMapper.readTree("""
                {
                    "fields": [{"name": "approvalDate", "type": "date"}]
                }
            """.trimIndent())

            every { mockFormRepository.findTopByFormIdOrderByVersionDesc(formId) } returns existingForm
            val expectedForm = Form(
                id = 2,
                formId = formId,
                name = formName,
                schema = schema,
                version = 3
            )
            every { mockFormRepository.save(any()) } returns expectedForm

            // Act
            val result = formService.deploy(formId, formName, schema)

            // Assert
            result.version shouldBe 3
            verify { mockFormRepository.save(any()) }
        }
    }

    context("getLatestVersionByFormId") {
        test("should retrieve latest version of form") {
            // Arrange
            val formId = "applicationForm"
            val form = Form(
                id = 2,
                formId = formId,
                name = "ApplicationForm",
                schema = objectMapper.readTree("{}"),
                version = 3
            )
            every { mockFormRepository.findTopByFormIdOrderByVersionDesc(formId) } returns form

            // Act
            val result = formService.getLatestVersionByFormId(formId)

            // Assert
            result shouldNotBe null
            result?.id shouldBe 2
            result?.formId shouldBe formId
            result?.version shouldBe 3
            verify { mockFormRepository.findTopByFormIdOrderByVersionDesc(formId) }
        }

        test("should return null when form not found") {
            // Arrange
            val formId = "missingForm"
            every { mockFormRepository.findTopByFormIdOrderByVersionDesc(formId) } returns null

            // Act
            val result = formService.getLatestVersionByFormId(formId)

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
                formId = "applicationForm",
                name = "ApplicationForm",
                schema = objectMapper.readTree("{}"),
                version = 1
            )
            every { mockFormRepository.findById(formId) } returns Optional.of(form)

            // Act
            val result = formService.getById(formId)

            // Assert
            result shouldNotBe null
            result?.id shouldBe formId
            result?.name shouldBe "ApplicationForm"
        }

        test("should return null when form not found") {
            // Arrange
            val formId = 999L
            every { mockFormRepository.findById(formId) } returns Optional.empty()

            // Act
            val result = formService.getById(formId)

            // Assert
            result shouldBe null
        }
    }

    context("getAllVersionsByFormId") {
        test("should retrieve all versions of a form") {
            // Arrange
            val formId = "applicationForm"
            val formName = "ApplicationForm"
            val form1 = Form(id = 1, formId = formId, name = formName, schema = objectMapper.readTree("{}"), version = 1)
            val form2 = Form(id = 2, formId = formId, name = formName, schema = objectMapper.readTree("{}"), version = 2)
            val form3 = Form(id = 3, formId = formId, name = formName, schema = objectMapper.readTree("{}"), version = 3)

            every { mockFormRepository.findByFormIdOrderByVersionAsc(formId) } returns listOf(form1, form2, form3)

            // Act
            val result = formService.getAllVersionsByFormId(formId)

            // Assert
            result shouldHaveSize 3
            result[0].version shouldBe 1
            result[1].version shouldBe 2
            result[2].version shouldBe 3
        }

        test("should return empty list when form not found") {
            // Arrange
            val formId = "missingForm"
            every { mockFormRepository.findByFormIdOrderByVersionAsc(formId) } returns emptyList()

            // Act
            val result = formService.getAllVersionsByFormId(formId)

            // Assert
            result.shouldBeEmpty()
        }
    }
})

