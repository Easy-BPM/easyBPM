package com.easy.bpm.repository.security

import com.easy.bpm.model.security.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun findByIdentityProviderAndExternalIdentityId(identityProvider: String, externalIdentityId: String): AppUser?
    fun existsByUsername(username: String): Boolean
    fun findAllByGroups_Id(groupId: Long): List<AppUser>
}

