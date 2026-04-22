package com.example.bpm.dto

import org.springframework.web.multipart.MultipartFile

/**
 * Request DTO for JAR file upload
 *
 * @param jarFile JAR file to upload (multipart)
 * @param description Optional description of the JAR
 */
data class CodeTaskJarUploadRequest(
    val jarFile: MultipartFile,
    val description: String? = null
)
