package com.easy.bpm.service.code

import com.easy.bpm.entity.CodeClassMetadata
import com.easy.bpm.entity.CodeTaskJar
import com.easy.bpm.repository.codetask.CodeClassMetadataRepository
import com.easy.bpm.repository.codetask.CodeTaskJarRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files

/**
 * CodeClassDiscoveryService - JAR loading and class/method discovery
 *
 * Responsibilities:
 * - Load JAR files as ClassLoaders
 * - Discover classes and methods via reflection
 * - Extract method metadata (signature, parameters, return type)
 * - Store metadata in database
 * - Handle invalid/corrupted JARs
 */
@Service
class CodeClassDiscoveryService(
  private val codeTaskJarRepository: CodeTaskJarRepository,
  private val codeClassMetadataRepository: CodeClassMetadataRepository,
  private val codeExecutionService: CodeExecutionService,
  private val objectMapper: ObjectMapper
) {
  private val logger = LoggerFactory.getLogger(CodeClassDiscoveryService::class.java)

  /**
   * Load a JAR file and create a ClassLoader
   *
   * @param jarBytes JAR file content
   * @return URLClassLoader for loading classes from JAR
   * @throws IllegalArgumentException if JAR is invalid
   */
  fun createClassLoader(jarBytes: ByteArray): URLClassLoader {
    return try {
      // Create temporary file for JAR
      val tempFile = Files.createTempFile("code-task-", ".jar").toFile()
      tempFile.deleteOnExit()

      // Write JAR content to temp file
      Files.write(tempFile.toPath(), jarBytes)

      // Create URLClassLoader
      val url = tempFile.toURI().toURL()
      URLClassLoader(arrayOf(url), this.javaClass.classLoader)
    } catch (ex: Exception) {
      logger.error("Failed to create ClassLoader from JAR bytes", ex)
      throw IllegalArgumentException("Invalid JAR file: ${ex.message}")
    }
  }

  /**
   * Discover all classes in a JAR file
   *
   * @param classLoader ClassLoader for JAR
   * @return List of fully qualified class names
   */
  fun discoverClasses(classLoader: URLClassLoader): List<String> {
    val classes = mutableListOf<String>()

    try {
      // Get JAR file path from URLClassLoader
      val jarUrls = classLoader.urLs
      if (jarUrls.isEmpty()) {
        return classes
      }

      val jarPath = jarUrls.first().path
      val jarFile = java.util.jar.JarFile(File(jarPath.replace("%20", " ")))

      jarFile.entries().asSequence()
        .filter { entry -> entry.name.endsWith(".class") }
        .filter { entry -> !entry.name.contains("$") } // Skip inner classes
        .forEach { entry ->
          val className = entry.name
            .replace("/", ".")
            .removeSuffix(".class")
          // Ignore system classes
          if (!className.startsWith("java.") && !className.startsWith("kotlin.")) {
            classes.add(className)
          }
        }

      jarFile.close()
    } catch (ex: Exception) {
      logger.warn("Could not enumerate classes from JAR", ex)
      // Fallback: return empty list
    }

    return classes
  }

  /**
   * Discover all public methods in a class that can be invoked
   *
   * Filters out:
   * - Private methods
   * - Protected methods
   * - Methods from Object superclass
   * - Synthetic methods
   *
   * @param clazz Class to inspect
   * @return List of invokable methods
   */
  fun discoverMethods(clazz: Class<*>): List<DiscoveredMethod> {
    return clazz.declaredMethods
      .filter { method ->
        // Filter public or default (package-private) methods
        val isAccessible = Modifier.isPublic(method.modifiers) || 
                          (!Modifier.isPrivate(method.modifiers) && !Modifier.isProtected(method.modifiers))

        // Exclude synthetic methods
        val isSynthetic = method.isSynthetic

        // Exclude methods from Object class
        val isFromObject = method.declaringClass == Object::class.java

        isAccessible && !isSynthetic && !isFromObject
      }
      .map { method ->
        val paramNames = method.parameterTypes.indices.map { "param$it" }.toTypedArray()
        DiscoveredMethod(
          methodName = method.name,
          returnType = method.returnType,
          parameters = method.parameterTypes.toList(),
          parameterNames = paramNames,
          isStatic = Modifier.isStatic(method.modifiers),
          signature = codeExecutionService.getMethodSignature(method)
        )
      }
  }

  /**
   * Extract metadata from a method
   *
   * @param jarId ID of JAR file
   * @param className Fully qualified class name
   * @param method Method to extract metadata from
   * @return CodeClassMetadata entity
   */
  fun extractMethodMetadata(
    jarId: Long,
    className: String,
    discoveredMethod: DiscoveredMethod
  ): CodeClassMetadata {
    val inputParams = discoveredMethod.parameters.mapIndexed { index, type ->
      mapOf(
        "name" to (if (index < discoveredMethod.parameterNames.size) discoveredMethod.parameterNames[index] else "param$index"),
        "type" to type.name,
        "simpleType" to type.simpleName
      )
    }

    return CodeClassMetadata(
      jarId = jarId,
      className = className,
      methodName = discoveredMethod.methodName,
      methodSignature = discoveredMethod.signature,
      inputParams = objectMapper.writeValueAsString(inputParams),
      returnType = discoveredMethod.returnType.name
    )
  }

  /**
   * Validate JAR file format (magic bytes check)
   *
   * @param jarBytes JAR file content
   * @return true if valid JAR format
   */
  fun isValidJar(jarBytes: ByteArray): Boolean {
    if (jarBytes.size < 4) return false

    // JAR files start with PK\x03\x04 (ZIP format)
    val magicBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    return jarBytes.take(4).toByteArray().contentEquals(magicBytes)
  }

  /**
   * Hash JAR file content (SHA-256)
   *
   * @param jarBytes JAR file content
   * @return SHA-256 hash as hex string
   */
  fun hashJar(jarBytes: ByteArray): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(jarBytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  /**
   * Store discovered metadata in database
   *
   * @param jarId ID of JAR
   * @param className Class name
   * @param methods List of discovered methods
   */
  @Transactional
  fun storeMethodMetadata(
    jarId: Long,
    className: String,
    methods: List<DiscoveredMethod>
  ) {
    methods.forEach { method ->
      val inputParams = listOf(
        mapOf("type" to "parameter")
      )
      // Note: In real implementation, would extract actual parameter info

      val metadata = CodeClassMetadata(
        jarId = jarId,
        className = className,
        methodName = method.methodName,
        methodSignature = method.signature,
        inputParams = objectMapper.writeValueAsString(inputParams),
        returnType = method.returnType.name
      )

      codeClassMetadataRepository.save(metadata)
      logger.debug("Stored metadata for {}.{}", className, method.methodName)
    }
  }
}

/**
 * Represents a discovered method in a class
 */
data class DiscoveredMethod(
  val methodName: String,
  val returnType: Class<*>,
  val parameters: List<Class<*>>,
  val parameterNames: Array<String>,
  val isStatic: Boolean,
  val signature: String
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DiscoveredMethod) return false
    if (methodName != other.methodName) return false
    if (!returnType.equals(other.returnType)) return false
    if (parameters != other.parameters) return false
    return true
  }

  override fun hashCode(): Int {
    var result = methodName.hashCode()
    result = 31 * result + returnType.hashCode()
    result = 31 * result + parameters.hashCode()
    return result
  }
}

