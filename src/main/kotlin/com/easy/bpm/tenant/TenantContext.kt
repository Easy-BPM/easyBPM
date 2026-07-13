package com.easy.bpm.tenant

object TenantContext {
    const val DEFAULT_TENANT = "default"
    const val TENANT_HEADER = "X-Tenant-Id"

    private val currentTenant = ThreadLocal<String>()

    fun getTenant(): String = currentTenant.get() ?: DEFAULT_TENANT

    fun setTenant(tenant: String?) {
        currentTenant.set(normalize(tenant))
    }

    fun clear() {
        currentTenant.remove()
    }

    fun normalize(tenant: String?): String = tenant?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: DEFAULT_TENANT
}
