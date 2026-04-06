package com.khanabook.lite.pos.domain.util

object UserMessageSanitizer {

    private val databaseFragments = listOf(
        "sqlite",
        "sqlcipher",
        "sqlite_master",
        "room",
        "syntax error",
        "no such table",
        "cursorwindow",
        "near \"",
        "while compiling",
        "constraint",
        "foreign key",
        "unique constraint"
    )

    private val networkFragments = listOf(
        "socket",
        "connect",
        "timeout",
        "no route to host",
        "network",
        "connection",
        "eof",
        "reset",
        "broken pipe",
        "host is unresolved",
        "unable to resolve host",
        "failed to connect"
    )

    private val authFragments = listOf(
        "401",
        "403",
        "unauthorized",
        "forbidden",
        "token",
        "jwt",
        "credential",
        "auth",
        "bearer"
    )

    private val serverErrorPatterns = listOf(
        "500",
        "502",
        "503",
        "504",
        "server error",
        "internal server"
    )

    private val sensitivePatterns = listOf(
        Regex("(https?://)?[\\w.-]+\\.[a-z]{2,}(/[\\w./-]*)?", RegexOption.IGNORE_CASE),
        Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?"),
        Regex("[a-zA-Z0-9.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        Regex("at [a-zA-Z0-9.$_]+\\([^)]+\\)")
    )

    fun sanitize(error: Throwable?, fallback: String): String {
        val message = error?.message?.trim().orEmpty()
        if (message.isBlank()) return fallback

        val lowered = message.lowercase()

        if (databaseFragments.any { it in lowered }) {
            return fallback
        }

        if (networkFragments.any { fragment -> lowered.contains(fragment) }) {
            return "Network error. Please check your connection."
        }

        if (authFragments.any { fragment -> lowered.contains(fragment) }) {
            return "Authentication error. Please login again."
        }

        if (serverErrorPatterns.any { pattern -> lowered.contains(pattern) }) {
            return "Server error. Please try again later."
        }

        if (sensitivePatterns.any { it.containsMatchIn(message) }) {
            return fallback
        }

        return message
    }

    fun sanitizeHttpError(code: Int, fallback: String): String {
        return when (code) {
            400 -> "Invalid request. Please check your input."
            401 -> "Session expired. Please login again."
            403 -> "Access denied. Please login again."
            404 -> "Resource not found. Please try again."
            409 -> "Conflict detected. Please refresh and try again."
            in 500..599 -> "Server error. Please try again later."
            else -> fallback
        }
    }

    fun sanitizeNetworkError(fallback: String): String {
        return "Network error. Please check your connection."
    }

    fun sanitizeGenericError(fallback: String): String {
        return fallback
    }
}
