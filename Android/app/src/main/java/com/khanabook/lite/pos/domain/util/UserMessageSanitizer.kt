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
        Regex("at [\\w.]+\\([^)]+\\)")
    )

    fun sanitize(error: Throwable?, fallback: String): String {
        return sanitizeWithDetails(error, fallback).message
    }

    fun sanitizeWithDetails(error: Throwable?, fallback: String): SanitizedBackendError {
        val parsed = when (error) {
            is BackendException -> error.details
            is retrofit2.HttpException -> BackendErrorParser.fromHttpException(error)
            else -> null
        }

        if (parsed != null) {
            return sanitizeBackendError(parsed, fallback)
        }

        val message = error?.message?.trim().orEmpty()
        if (message.isBlank()) return SanitizedBackendError(message = fallback)

        val lowered = message.lowercase()

        if (databaseFragments.any { it in lowered }) {
            return SanitizedBackendError(message = fallback)
        }

        if (error is javax.net.ssl.SSLHandshakeException || error is javax.net.ssl.SSLPeerUnverifiedException) {
            return SanitizedBackendError(message = "Secure connection failed. Please check your system date or update the app.")
        }

        if (networkFragments.any { fragment -> lowered.contains(fragment) }) {
            return SanitizedBackendError(message = "Network error. Please check your connection.")
        }

        if (authFragments.any { fragment -> lowered.contains(fragment) }) {
            return SanitizedBackendError(message = "Authentication error. Please login again.")
        }

        if (serverErrorPatterns.any { pattern -> lowered.contains(pattern) }) {
            return SanitizedBackendError(message = "Server error. Please try again later.")
        }

        if (sensitivePatterns.any { it.containsMatchIn(message) }) {
            return SanitizedBackendError(message = fallback)
        }

        return SanitizedBackendError(message = fallback)
    }

    fun sanitizeBackendError(error: ParsedBackendError, fallback: String): SanitizedBackendError {
        val fallbackMessage = error.statusCode?.let { sanitizeHttpError(it, fallback) } ?: fallback
        if (error.statusCode == 409) {
            return SanitizedBackendError(
                message = fallbackMessage,
                fieldErrors = emptyMap(),
                path = error.path,
                errorId = error.errorId,
                statusCode = error.statusCode
            )
        }
        val sanitizedFieldErrors = error.fieldErrors.mapValuesNotNull { (_, value) ->
            val sanitized = sanitizeBackendMessage(value, "")
            sanitized.takeIf { it.isNotBlank() }
        }
        val primaryMessage = sanitizeBackendMessage(error.message, fallbackMessage)
        val message = if (primaryMessage == fallbackMessage && sanitizedFieldErrors.isNotEmpty()) {
            sanitizedFieldErrors.values.first()
        } else {
            primaryMessage
        }

        return SanitizedBackendError(
            message = message,
            fieldErrors = sanitizedFieldErrors,
            path = error.path,
            errorId = error.errorId,
            statusCode = error.statusCode
        )
    }

    fun sanitizeBackendMessage(message: String?, fallback: String): String {
        val trimmedMessage = message?.trim().orEmpty()
        if (trimmedMessage.isBlank()) return fallback

        val lowered = trimmedMessage.lowercase()
        if (databaseFragments.any { it in lowered }) return fallback
        if (networkFragments.any { fragment -> lowered.contains(fragment) }) {
            return "Network error. Please check your connection."
        }
        if (authFragments.any { fragment -> lowered.contains(fragment) }) {
            return "Authentication error. Please login again."
        }
        if (serverErrorPatterns.any { pattern -> lowered.contains(pattern) }) {
            return "Server error. Please try again later."
        }
        if (sensitivePatterns.any { it.containsMatchIn(trimmedMessage) }) return fallback
        return fallback
    }

    fun sanitizeHttpError(code: Int, fallback: String): String {
        return when (code) {
            400 -> "Invalid request. Please check your input."
            401 -> "Session expired. Please login again."
            403 -> "Access denied. Please login again."
            404 -> "Resource not found. Please try again."
            409 -> SYNC_CONFLICT_MESSAGE
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

    private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
        val result = LinkedHashMap<K, R>()
        for (entry in entries) {
            transform(entry)?.let { result[entry.key] = it }
        }
        return result
    }
}
