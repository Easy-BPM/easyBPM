package com.easy.bpm.dto

/**
 * Response DTO for successful JAR upload
 *
 * @param jarId ID of uploaded JAR
 * @param fileName Original file name
 * @param fileHash SHA-256 hash of JAR
 * @param uploadedAt ISO timestamp of upload
 * @param classCount Number of classes discovered
 * @param methodCount Number of methods discovered
 */
data class CodeTaskJarUploadResponse(
    val jarId: Long,
    val fileName: String,
    val fileHash: String,
    val uploadedAt: String,
    val classCount: Int,
    val methodCount: Int,
    val classes: List<String>
)

