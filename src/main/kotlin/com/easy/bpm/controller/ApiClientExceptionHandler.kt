package com.easy.bpm.controller

import com.easy.bpm.dto.security.ApiClientErrorResponse
import com.easy.bpm.service.admin.ApiClientException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import java.time.LocalDateTime
import com.easy.bpm.security.RequestCorrelation

@RestControllerAdvice(assignableTypes = [ApiClientAdminController::class])
class ApiClientExceptionHandler {
    @ExceptionHandler(ApiClientException::class)
    fun handle(exception: ApiClientException, request: HttpServletRequest): ResponseEntity<ApiClientErrorResponse> {
        return response(exception.status, exception.code, exception.message, request, exception.fieldErrors)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun handleInvalidRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiClientErrorResponse> =
        response(HttpStatus.BAD_REQUEST, "INVALID_API_CLIENT_REQUEST", "The API client request is invalid", request)

    private fun response(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        fieldErrors: Map<String, String> = emptyMap()
    ): ResponseEntity<ApiClientErrorResponse> {
        val correlationId = RequestCorrelation.id(request)
        return ResponseEntity.status(status).body(
            ApiClientErrorResponse(
                timestamp = LocalDateTime.now(),
                status = status.value(),
                code = code,
                message = message,
                path = request.requestURI,
                correlationId = correlationId,
                fieldErrors = fieldErrors
            )
        )
    }
}
