package com.easy.bpm.security

object ApiClientCredential {
    const val PREFIX = "ebpm_"
    private val pattern = Regex("^ebpm_([A-Za-z0-9_-]{16})\\.([A-Za-z0-9_-]{43})$")

    fun isNativeBearer(header: String?): Boolean =
        header?.startsWith("Bearer $PREFIX") == true

    fun parse(header: String?): Pair<String, String>? {
        if (!isNativeBearer(header)) return null
        val raw = header!!.substring(7)
        val match = pattern.matchEntire(raw) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }
}
