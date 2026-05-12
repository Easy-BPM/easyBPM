package com.easy.bpm.dto.security

data class CreateUserRequest(
    val username: String,
    val password: String,
    val enabled: Boolean = true,
    val groupIds: Set<Long> = emptySet(),
    val permissionCodes: Set<String> = emptySet()
)

data class UpdateUserRequest(
    val enabled: Boolean,
    val groupIds: Set<Long> = emptySet(),
    val permissionCodes: Set<String> = emptySet()
)

data class ResetPasswordRequest(
    val password: String
)

data class UserResponse(
    val id: Long,
    val username: String,
    val enabled: Boolean,
    val groups: Set<String>,
    val permissions: Set<String>
)

data class CreateGroupRequest(
    val code: String,
    val name: String,
    val permissionCodes: Set<String> = emptySet()
)

data class UpdateGroupRequest(
    val name: String,
    val permissionCodes: Set<String> = emptySet()
)

data class GroupResponse(
    val id: Long,
    val code: String,
    val name: String,
    val permissions: Set<String>
)

data class UpdateGroupUsersRequest(
    val userIds: Set<Long> = emptySet()
)

