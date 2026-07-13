package com.easy.bpm.service

import com.easy.bpm.dto.security.*
import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import com.easy.bpm.tenant.TenantContext
import java.time.LocalDateTime

@Service
class AdminSecurityService(
    private val appUserRepository: AppUserRepository,
    private val userGroupRepository: UserGroupRepository,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun listUsers(): List<UserResponse> = appUserRepository.findAllByTenantId(TenantContext.getTenant()).map(::toUserResponse)

    @Transactional
    fun createUser(request: CreateUserRequest, actor: String): UserResponse {
        val tenantId = TenantContext.getTenant()
        if (appUserRepository.existsByTenantIdAndUsername(tenantId, request.username)) {
            throw IllegalArgumentException("Username already exists")
        }

        val groups = if (request.groupIds.isEmpty()) emptySet() else userGroupRepository.findAllById(request.groupIds).toSet()
        val permissions = if (request.permissionCodes.isEmpty()) emptySet() else permissionRepository.findAllByCodeIn(request.permissionCodes).toSet()

        val user = appUserRepository.save(
            AppUser(
                username = request.username,
                tenantId = tenantId,
                passwordHash = passwordEncoder.encode(request.password),
                enabled = request.enabled,
                groups = groups.toMutableSet(),
                permissions = permissions.toMutableSet(),
                createdBy = actor,
                updatedBy = actor
            )
        )

        return toUserResponse(user)
    }

    @Transactional
    fun updateUser(userId: Long, request: UpdateUserRequest, actor: String): UserResponse {
        val user = appUserRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        user.enabled = request.enabled
        user.groups = userGroupRepository.findAllById(request.groupIds).toMutableSet()
        user.permissions = permissionRepository.findAllByCodeIn(request.permissionCodes).toMutableSet()
        user.updatedAt = LocalDateTime.now()
        user.updatedBy = actor
        return toUserResponse(appUserRepository.save(user))
    }

    @Transactional
    fun resetPassword(userId: Long, request: ResetPasswordRequest, actor: String) {
        val user = appUserRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        user.passwordHash = passwordEncoder.encode(request.password)
        user.updatedAt = LocalDateTime.now()
        user.updatedBy = actor
        appUserRepository.save(user)
    }

    fun deleteUser(userId: Long) {
        appUserRepository.deleteById(userId)
    }

    fun listGroups(): List<GroupResponse> = userGroupRepository.findAllByTenantId(TenantContext.getTenant()).map(::toGroupResponse)

    @Transactional
    fun createGroup(request: CreateGroupRequest): GroupResponse {
        val permissions = permissionRepository.findAllByCodeIn(request.permissionCodes).toMutableSet()
        val group = userGroupRepository.save(
            UserGroup(
                code = request.code,
                tenantId = TenantContext.getTenant(),
                name = request.name,
                permissions = permissions
            )
        )
        return toGroupResponse(group)
    }

    @Transactional
    fun updateGroup(groupId: Long, request: UpdateGroupRequest): GroupResponse {
        val group = userGroupRepository.findById(groupId).orElseThrow { IllegalArgumentException("Group not found") }
        group.name = request.name
        group.permissions = permissionRepository.findAllByCodeIn(request.permissionCodes).toMutableSet()
        group.updatedAt = LocalDateTime.now()
        return toGroupResponse(userGroupRepository.save(group))
    }

    fun deleteGroup(groupId: Long) {
        userGroupRepository.deleteById(groupId)
    }

    fun getGroupUsers(groupId: Long): List<UserResponse> {
        userGroupRepository.findById(groupId).orElseThrow { IllegalArgumentException("Group not found") }
        return appUserRepository.findAllByTenantIdAndGroups_Id(TenantContext.getTenant(), groupId).map(::toUserResponse)
    }

    @Transactional
    fun updateGroupUsers(groupId: Long, userIds: Set<Long>, actor: String): List<UserResponse> {
        val group = userGroupRepository.findById(groupId).orElseThrow { IllegalArgumentException("Group not found") }
        val targetUsers = appUserRepository.findAllById(userIds).associateBy { it.id }

        // Remove users currently in group but not selected
        val currentlyInGroup = appUserRepository.findAllByTenantIdAndGroups_Id(TenantContext.getTenant(), groupId)
        currentlyInGroup
            .filter { !userIds.contains(it.id) }
            .forEach { user ->
                user.groups = user.groups.filter { it.id != group.id }.toMutableSet()
                user.updatedAt = LocalDateTime.now()
                user.updatedBy = actor
                appUserRepository.save(user)
            }

        // Add selected users to group
        targetUsers.values.forEach { user ->
            if (user.groups.none { it.id == group.id }) {
                user.groups.add(group)
                user.updatedAt = LocalDateTime.now()
                user.updatedBy = actor
                appUserRepository.save(user)
            }
        }

        return appUserRepository.findAllByTenantIdAndGroups_Id(TenantContext.getTenant(), groupId).map(::toUserResponse)
    }

    private fun toUserResponse(user: AppUser) = UserResponse(
        id = user.id,
        username = user.username,
        tenantId = user.tenantId,
        enabled = user.enabled,
        groups = user.groups.map { it.code }.toSet(),
        permissions = user.permissions.map { it.code }.toSet()
    )

    private fun toGroupResponse(group: UserGroup) = GroupResponse(
        id = group.id,
        code = group.code,
        tenantId = group.tenantId,
        name = group.name,
        permissions = group.permissions.map { it.code }.toSet()
    )
}

