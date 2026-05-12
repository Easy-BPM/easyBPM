package com.easy.bpm.repository.security

import com.easy.bpm.model.security.UserGroup
import org.springframework.data.jpa.repository.JpaRepository

interface UserGroupRepository : JpaRepository<UserGroup, Long> {
    fun findByCode(code: String): UserGroup?
}

