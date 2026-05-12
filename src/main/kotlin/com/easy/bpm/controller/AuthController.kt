package com.easy.bpm.controller

import com.easy.bpm.dto.security.CurrentUserResponse
import com.easy.bpm.dto.security.LoginRequest
import com.easy.bpm.dto.security.LoginResponse
import com.easy.bpm.service.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse = authService.login(request)

    @GetMapping("/me")
    fun me(): CurrentUserResponse = authService.me()
}

