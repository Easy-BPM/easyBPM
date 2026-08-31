package com.easy.bpm.security

object AppPermissions {
    const val ACCESS_BPM_ADMIN = "ACCESS_BPM_ADMIN"
    const val ACCESS_PROCESS_PORTAL = "ACCESS_PROCESS_PORTAL"
    const val ACCESS_BPM_MODELER = "ACCESS_BPM_MODELER"
    const val VIEW_USERS = "VIEW_USERS"
    const val MANAGE_USERS = "MANAGE_USERS"
    const val VIEW_GROUPS = "VIEW_GROUPS"
    const val MANAGE_GROUPS = "MANAGE_GROUPS"
    const val MANAGE_PERMISSIONS = "MANAGE_PERMISSIONS"

    val all = setOf(
        ACCESS_BPM_ADMIN,
        ACCESS_PROCESS_PORTAL,
        ACCESS_BPM_MODELER,
        VIEW_USERS,
        MANAGE_USERS,
        VIEW_GROUPS,
        MANAGE_GROUPS,
        MANAGE_PERMISSIONS
    )
}

