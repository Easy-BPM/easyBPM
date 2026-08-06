package com.easy.bpm.controller

import com.easy.bpm.dto.*
import com.easy.bpm.entity.CodeTaskJar
import com.easy.bpm.entity.CodeTaskExecutionAudit
import com.easy.bpm.repository.CodeTaskJarRepository
import com.easy.bpm.repository.CodeClassMetadataRepository
import com.easy.bpm.repository.CodeTaskExecutionAuditRepository
import com.easy.bpm.service.code.CodeClassDiscoveryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLClassLoader
import java.time.LocalDateTime

/**
 * REST Controller for Code Task management
 *
 * Endpoints:
 * - POST /code-tasks/upload - Upload JAR and discover classes/methods
 * - GET /code-tasks/jar/{jarId}/classes - List classes in JAR
 * - GET /code-tasks/jar/{jarId}/classes/{className}/methods - List methods in class
 * - GET /code-tasks/executions - List execution history with filtering
 */
@RestController
@RequestMapping("/code-tasks")
class CodeTaskController(
    private val jarRepository: CodeTaskJarRepository,
    private val classMetadataRepository: CodeClassMetadataRepository,
    private val executionAuditRepository: CodeTaskExecutionAuditRepository,
    private val discoveryService: CodeClassDiscoveryService
) {

    private val logger = LoggerFactory.getLogger(CodeTaskController::class.java)

    /**
     * POST /code-tasks/upload
     *
     * Upload JAR file and discover classes/methods
     *
     * @param jarFile JAR file (multipart)
     * @param description Optional description
     * @return Upload response with discovered classes
     */
    @PostMapping("/upload")
    fun uploadJar(
        @RequestParam("jarFile") jarFile: MultipartFile,
        @RequestParam("description", required = false) description: String?
    ): ResponseEntity<CodeTaskJarUploadResponse> {
        return try {
            logger.info("Uploading JAR file: {}", jarFile.originalFilename)

            // Read JAR content
            val jarBytes = jarFile.bytes
            if (jarBytes.isEmpty()) {
                return ResponseEntity.badRequest().build()
            }

            // Validate JAR
            if (!discoveryService.isValidJar(jarBytes)) {
                logger.warn("Invalid JAR file format: {}", jarFile.originalFilename)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }

            // Check for duplicate
            val fileHash = discoveryService.hashJar(jarBytes)
            val existing = jarRepository.findByFileHash(fileHash)
            if (existing != null) {
                logger.info("JAR already uploaded with hash: {}", fileHash)
                // Return existing JAR info
                return getUploadResponseForJar(existing)
            }

            // Save JAR
            val jar = CodeTaskJar(
                content = jarBytes,
                fileName = jarFile.originalFilename ?: "unknown.jar",
                fileHash = fileHash,
                uploadDate = LocalDateTime.now(),
                uploadedBy = "admin", // TODO: Get from SecurityContext
                description = description
            )
            val savedJar = jarRepository.save(jar)
            logger.info("JAR saved with ID: {}", savedJar.id)

            // Discover classes and methods
            val classLoader = discoveryService.createClassLoader(jarBytes)
            val discoveredClasses = discoveryService.discoverClasses(classLoader)
            logger.info("Discovered {} classes", discoveredClasses.size)

            // Store metadata for each class
            var totalMethods = 0
            for (className in discoveredClasses) {
                try {
                    val clazz = classLoader.loadClass(className)
                    val methods = discoveryService.discoverMethods(clazz)
                    
                    for (method in methods) {
                        val metadata = discoveryService.extractMethodMetadata(
                            savedJar.id!!,
                            className,
                            method
                        )
                        classMetadataRepository.save(metadata)
                        totalMethods++
                    }
                } catch (ex: Exception) {
                    logger.warn("Could not process class {}: {}", className, ex.message)
                }
            }

            classLoader.close()

            logger.info("Stored metadata for {} methods", totalMethods)

            // Return response
            ResponseEntity.ok(
                CodeTaskJarUploadResponse(
                    jarId = savedJar.id!!,
                    fileName = savedJar.fileName,
                    fileHash = savedJar.fileHash,
                    uploadedAt = savedJar.uploadDate.toString(),
                    classCount = discoveredClasses.size,
                    methodCount = totalMethods,
                    classes = discoveredClasses
                )
            )
        } catch (ex: Exception) {
            logger.error("Error uploading JAR", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * GET /code-tasks/jar/{jarId}/classes
     *
     * List all classes in a JAR file
     *
     * @param jarId ID of JAR
     * @return List of classes
     */
    @GetMapping("/jar/{jarId}/classes")
    fun getJarClasses(@PathVariable jarId: Long): ResponseEntity<JarClassesResponse> {
        return try {
            val jar = jarRepository.findById(jarId).orElse(null)
                ?: return ResponseEntity.notFound().build()

            val metadata = classMetadataRepository.findByJarId(jarId)
            val classes = metadata.map { it.className }.distinct()

            ResponseEntity.ok(
                JarClassesResponse(
                    jarId = jarId,
                    fileName = jar.fileName,
                    classes = classes
                )
            )
        } catch (ex: Exception) {
            logger.error("Error fetching JAR classes", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * GET /code-tasks/jar/{jarId}/classes/{className}/methods
     *
     * List all methods in a class
     *
     * @param jarId ID of JAR
     * @param className Fully qualified class name
     * @return Class with methods
     */
    @GetMapping("/jar/{jarId}/classes/{className}/methods")
    fun getClassMethods(
        @PathVariable jarId: Long,
        @PathVariable className: String
    ): ResponseEntity<ClassMetadataResponse> {
        return try {
            val metadata = classMetadataRepository
                .findByJarIdAndClassName(jarId, className)

            if (metadata.isEmpty()) {
                return ResponseEntity.notFound().build()
            }

            val methods = metadata.map { m ->
                MethodMetadataResponse(
                    methodName = m.methodName,
                    returnType = m.returnType ?: "void",
                    signature = m.methodSignature ?: "unknown",
                    parameters = emptyList(), // TODO: Parse from JSONB
                    parameterNames = emptyList(), // TODO: Extract from signature
                    isStatic = false // TODO: Store in metadata
                )
            }

            ResponseEntity.ok(
                ClassMetadataResponse(
                    className = className,
                    methods = methods
                )
            )
        } catch (ex: Exception) {
            logger.error("Error fetching class methods", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * GET /code-tasks/executions
     *
     * List execution history with filtering and pagination
     *
     * @param instanceId Filter by process instance ID (optional)
     * @param status Filter by status: COMPLETED, FAILED, TIMEOUT (optional)
     * @param page Page number (default 0)
     * @param size Page size (default 20)
     * @return Paginated execution audit records
     */
    @GetMapping("/executions")
    fun getExecutions(
        @RequestParam(required = false) instanceId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        pageable: Pageable
    ): ResponseEntity<ExecutionAuditPageResponse> {
        return try {
            val pageRequest = org.springframework.data.domain.PageRequest.of(page, size)

            val auditPage: Page<CodeTaskExecutionAudit> = when {
                instanceId != null && status != null ->
                    executionAuditRepository.findByInstanceIdAndStatus(instanceId, status, pageRequest) as Page<CodeTaskExecutionAudit>
                instanceId != null ->
                    executionAuditRepository.findByInstanceId(instanceId, pageRequest) as Page<CodeTaskExecutionAudit>
                status != null ->
                    executionAuditRepository.findByStatus(status, pageRequest) as Page<CodeTaskExecutionAudit>
                else ->
                    executionAuditRepository.findAll(pageRequest) as Page<CodeTaskExecutionAudit>
            }

            val content = auditPage.content.map { audit ->
                CodeTaskExecutionAuditResponse(
                    executionId = audit.id!!,
                    instanceId = audit.instanceId,
                    nodeId = audit.nodeId,
                    jarId = audit.jarId,
                    className = audit.className,
                    methodName = audit.methodName,
                    inputVariables = audit.inputVariables,
                    outputVariables = audit.outputVariables,
                    executionTimeMs = audit.executionTimeMs,
                    status = audit.status,
                    errorMessage = audit.errorMessage,
                    executedAt = audit.executedAt.toString()
                )
            }

            ResponseEntity.ok(
                ExecutionAuditPageResponse(
                    content = content,
                    totalElements = auditPage.totalElements,
                    totalPages = auditPage.totalPages,
                    currentPage = page
                )
            )
        } catch (ex: Exception) {
            logger.error("Error fetching executions", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    // Private helper method
    private fun getUploadResponseForJar(jar: CodeTaskJar): ResponseEntity<CodeTaskJarUploadResponse> {
        val metadata = classMetadataRepository.findByJarId(jar.id!!)
        val classes = metadata.map { it.className }.distinct()
        
        return ResponseEntity.ok(
            CodeTaskJarUploadResponse(
                jarId = jar.id!!,
                fileName = jar.fileName,
                fileHash = jar.fileHash,
                uploadedAt = jar.uploadDate.toString(),
                classCount = classes.size,
                methodCount = metadata.size,
                classes = classes
            )
        )
    }
}

