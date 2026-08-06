package com.easy.bpm.controller

import com.easy.bpm.dto.security.*
import com.easy.bpm.security.AuthenticatedUser
import com.easy.bpm.service.admin.AdminSecurityService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin")
class AdminSecurityController(
    private val adminSecurityService: AdminSecurityService
) {

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    fun listUsers(): List<UserResponse> = adminSecurityService.listUsers()

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    fun createUser(
        @RequestBody request: CreateUserRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser
    ): UserResponse = adminSecurityService.createUser(request, principal.username)

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser
    ): UserResponse = adminSecurityService.updateUser(id, request, principal.username)

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    fun resetPassword(
        @PathVariable id: Long,
        @RequestBody request: ResetPasswordRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser
    ): ResponseEntity<Void> {
        adminSecurityService.resetPassword(id, request, principal.username)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        adminSecurityService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun listGroups(): List<GroupResponse> = adminSecurityService.listGroups()

    @PostMapping("/groups")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun createGroup(@RequestBody request: CreateGroupRequest): GroupResponse = adminSecurityService.createGroup(request)

    @PutMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun updateGroup(@PathVariable id: Long, @RequestBody request: UpdateGroupRequest): GroupResponse =
        adminSecurityService.updateGroup(id, request)

    @DeleteMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun deleteGroup(@PathVariable id: Long): ResponseEntity<Void> {
        adminSecurityService.deleteGroup(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/groups/{id}/users")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun getGroupUsers(@PathVariable id: Long): List<UserResponse> = adminSecurityService.getGroupUsers(id)

    @PutMapping("/groups/{id}/users")
    @PreAuthorize("hasAuthority('MANAGE_GROUPS')")
    fun updateGroupUsers(
        @PathVariable id: Long,
        @RequestBody request: UpdateGroupUsersRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser
    ): List<UserResponse> = adminSecurityService.updateGroupUsers(id, request.userIds, principal.username)
}

