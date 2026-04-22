package com.easy.bpm.dto

/**
 * DTO for method metadata with parameter information
 *
 * @param methodName Name of method
 * @param returnType Fully qualified return type
 * @param signature Human-readable method signature
 * @param parameters List of parameter types
 * @param parameterNames List of parameter names (generated as param0, param1, etc.)
 */
data class MethodMetadataResponse(
    val methodName: String,
    val returnType: String,
    val signature: String,
    val parameters: List<String>,
    val parameterNames: List<String>,
    val isStatic: Boolean
)

/**
 * DTO for class with its methods
 *
 * @param className Fully qualified class name
 * @param methods List of methods in this class
 */
data class ClassMetadataResponse(
    val className: String,
    val methods: List<MethodMetadataResponse>
)

/**
 * DTO for JAR class list
 *
 * @param jarId ID of JAR
 * @param fileName File name
 * @param classes List of classes in JAR
 */
data class JarClassesResponse(
    val jarId: Long,
    val fileName: String,
    val classes: List<String>
)

