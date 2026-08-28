package com.easy.bpm.controller

import com.easy.bpm.dto.security.CurrentUserResponse
import com.easy.bpm.service.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val authService: AuthService
) {
    @GetMapping("/me")
    fun me(): CurrentUserResponse = authService.me()
}
