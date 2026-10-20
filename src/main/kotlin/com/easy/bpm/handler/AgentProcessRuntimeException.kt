package com.easy.bpm.handler

/** Expected provider failure: safe for persistence, API responses and boundary diagnostics. */
class AgentProcessRuntimeException(code: String?) : IllegalStateException(messageFor(code)) {
    val errorCode: String = normalize(code)
    companion object {
        const val PREFIX = "Agent runtime failure ["
        private val allowed = setOf("AUTH_ERROR", "RATE_LIMIT", "TIMEOUT", "PROVIDER_ERROR", "PARSE_ERROR", "INVALID_CONFIG", "NETWORK_ERROR", "MODEL_NOT_FOUND", "QUOTA_EXCEEDED", "UNKNOWN")
        private fun normalize(code: String?) = code?.takeIf { it in allowed } ?: "PROVIDER_ERROR"
        private fun messageFor(code: String?) = "$PREFIX${normalize(code)}]. Review the agent provider configuration and availability."
        fun codeFromMessage(message: String?): String? = message?.takeIf { it.startsWith(PREFIX) }
            ?.substringAfter(PREFIX)?.substringBefore(']')?.takeIf { it in allowed }
    }
}
