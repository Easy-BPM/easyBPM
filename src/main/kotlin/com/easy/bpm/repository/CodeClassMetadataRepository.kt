package com.easy.bpm.repository

import com.easy.bpm.entity.CodeClassMetadata
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for CodeClassMetadata - Class and method discovery
 */
@Repository
interface CodeClassMetadataRepository : JpaRepository<CodeClassMetadata, Long> {

  /**
   * Find all methods in a specific class within a JAR
   */
  fun findByJarIdAndClassName(jarId: Long, className: String): List<CodeClassMetadata>

  /**
   * Find specific method in a specific class in a JAR
   */
  fun findByJarIdAndClassNameAndMethodName(
    jarId: Long,
    className: String,
    methodName: String
  ): CodeClassMetadata?

  /**
   * Find all classes and methods in a JAR
   */
  fun findByJarId(jarId: Long): List<CodeClassMetadata>

  /**
   * Find all classes with a specific name across all JARs
   */
  fun findByClassName(className: String): List<CodeClassMetadata>
}

