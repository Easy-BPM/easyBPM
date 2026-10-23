package com.easy.bpm.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [TaskController::class, TaskSlaController::class, ProcessController::class])
class SlaRequestExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(ex: IllegalArgumentException): ResponseEntity<Map<String,String>> =
        ResponseEntity.badRequest().body(mapOf("message" to (ex.message ?: "Invalid request").take(1000)))
}
