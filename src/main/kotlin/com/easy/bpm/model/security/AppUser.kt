package com.easy.bpm.model.security

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_user")
data class AppUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var username: String,

    @Column(nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "identity_provider", nullable = false, length = 50)
    var identityProvider: String = "LOCAL",

    @Column(name = "external_identity_id", length = 200)
    var externalIdentityId: String? = null,

    @Column(length = 255)
    var email: String? = null,

    @Column(name = "first_name", length = 100)
    var firstName: String? = null,

    @Column(name = "last_name", length = 100)
    var lastName: String? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "app_user_group",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "group_id")]
    )
    var groups: MutableSet<UserGroup> = mutableSetOf(),

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "app_user_permission",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    var permissions: MutableSet<Permission> = mutableSetOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, length = 100)
    var createdBy: String = "system",

    @Column(nullable = false, length = 100)
    var updatedBy: String = "system"
)

