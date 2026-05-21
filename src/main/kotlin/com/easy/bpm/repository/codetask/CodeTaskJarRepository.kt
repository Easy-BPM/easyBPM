package com.easy.bpm.repository

import com.easy.bpm.entity.CodeTaskJar
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for CodeTaskJar - JAR file persistence
 */
@Repository
interface CodeTaskJarRepository : JpaRepository<CodeTaskJar, Long> {

  /**
   * Find JAR by file hash (for deduplication)
   */
  fun findByFileHash(fileHash: String): CodeTaskJar?

  /**
   * Find all JARs uploaded by a specific user
   */
  fun findByUploadedBy(uploadedBy: String): List<CodeTaskJar>

  /**
   * Find all JARs (for admin listing)
   */
  override fun findAll(): List<CodeTaskJar>
}

