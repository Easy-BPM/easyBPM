package com.example.bpm.repository

import com.example.bpm.entity.CodeTaskJar
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration Tests for CodeTaskJarRepository
 *
 * Tests CRUD operations and persistence of JAR files in database
 */
@DataJpaTest
class CodeTaskJarRepositoryTest {

  @Autowired
  private lateinit var repository: CodeTaskJarRepository

  @BeforeEach
  fun cleanup() {
    repository.deleteAll()
  }

  @Test
  fun `should save and retrieve JAR file`() {
    // Arrange
    val jarContent = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(100) // PK.... + dummy data
    val jar = CodeTaskJar(
      content = jarContent,
      fileName = "test-jar-1.0.jar",
      fileHash = "abc123def456"
    )

    // Act
    val saved = repository.save(jar)
    val retrieved = repository.findById(saved.id!!).orElse(null)

    // Assert
    assertNotNull(retrieved)
    assertEquals(saved.fileName, retrieved.fileName)
    assertEquals(saved.fileHash, retrieved.fileHash)
  }

  @Test
  fun `should enforce unique file hash constraint`() {
    // Arrange
    val hash = "unique-hash-123"
    val jar1 = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "jar1.jar",
      fileHash = hash
    )
    val jar2 = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "jar2.jar",
      fileHash = hash
    )

    // Act & Assert
    repository.save(jar1)
    org.junit.jupiter.api.assertThrows<Exception> {
      repository.saveAndFlush(jar2)
    }
  }

  @Test
  fun `should find JAR by file hash`() {
    // Arrange
    val hash = "findme-hash"
    val jar = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "test.jar",
      fileHash = hash
    )
    repository.save(jar)

    // Act
    val found = repository.findByFileHash(hash)

    // Assert
    assertNotNull(found)
    assertEquals(hash, found.fileHash)
  }

  @Test
  fun `should return null for non-existent file hash`() {
    // Act
    val found = repository.findByFileHash("non-existent-hash")

    // Assert
    assertNull(found)
  }

  @Test
  fun `should find all JARs uploaded by user`() {
    // Arrange
    val user = "alice@company.com"
    val jar1 = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "jar1.jar",
      fileHash = "hash1",
      uploadedBy = user
    )
    val jar2 = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "jar2.jar",
      fileHash = "hash2",
      uploadedBy = user
    )
    val jar3 = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "jar3.jar",
      fileHash = "hash3",
      uploadedBy = "bob@company.com"
    )
    repository.saveAll(listOf(jar1, jar2, jar3))

    // Act
    val userJars = repository.findByUploadedBy(user)

    // Assert
    assertEquals(2, userJars.size)
    assert(userJars.all { it.uploadedBy == user })
  }

  @Test
  fun `should find all JARs`() {
    // Arrange
    val jars = (1..5).map { i ->
      CodeTaskJar(
        content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        fileName = "jar$i.jar",
        fileHash = "hash$i"
      )
    }
    repository.saveAll(jars)

    // Act
    val all = repository.findAll()

    // Assert
    assertEquals(5, all.size)
  }

  @Test
  fun `should delete JAR by ID`() {
    // Arrange
    val jar = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "delete-me.jar",
      fileHash = "delete-hash"
    )
    val saved = repository.save(jar)

    // Act
    repository.deleteById(saved.id!!)
    val found = repository.findById(saved.id!!).orElse(null)

    // Assert
    assertNull(found)
  }

  @Test
  fun `should update JAR description`() {
    // Arrange
    val jar = CodeTaskJar(
      content = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
      fileName = "test.jar",
      fileHash = "hash123",
      description = "Original description"
    )
    val saved = repository.save(jar)

    // Act
    val updated = CodeTaskJar(
      id = saved.id,
      content = saved.content,
      fileName = saved.fileName,
      fileHash = saved.fileHash,
      uploadDate = saved.uploadDate,
      uploadedBy = saved.uploadedBy,
      description = "Updated description"
    )
    repository.save(updated)

    val retrieved = repository.findById(saved.id!!).orElse(null)

    // Assert
    assertNotNull(retrieved)
    assertEquals("Updated description", retrieved.description)
  }
}
