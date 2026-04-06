package com.khanabook.lite.pos.domain.util

object UserMessageSanitizer {
    private val sensitiveFragments = listOf(
        "sqlite",
        "sqlcipher",
        "sqlite_master",
        "database",
        "room",
        "syntax error",
        "no such table",
        "select ",
        "insert ",
        "update ",
        "delete ",
        "pragma ",
        "cursorwindow",
        "near \"",
        "while compiling"
    )

    fun sanitize(error: Throwable?, fallback: String): String {
        val message = error?.message?.trim().orEmpty()
        if (message.isBlank()) return fallback

        val lowered = message.lowercase()
        if (sensitiveFragments.any { it in lowered }) {
            return fallback
        }

        return message
    }
}
