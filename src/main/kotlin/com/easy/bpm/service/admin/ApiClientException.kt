package com.easy.bpm.service.admin

import org.springframework.http.HttpStatus

class ApiClientException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val fieldErrors: Map<String, String> = emptyMap()
) : RuntimeException(message)
