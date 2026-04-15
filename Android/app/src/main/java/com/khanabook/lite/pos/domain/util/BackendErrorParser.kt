package com.khanabook.lite.pos.domain.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException

const val SYNC_CONFLICT_MESSAGE = "Data changed on another device. Please sync and retry."

data class ParsedBackendError(
    val message: String?,
    val fieldErrors: Map<String, String> = emptyMap(),
    val path: String? = null,
    val errorId: String? = null,
    val statusCode: Int? = null
)

data class SanitizedBackendError(
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val path: String? = null,
    val errorId: String? = null,
    val statusCode: Int? = null
)

open class BackendException(
    val details: ParsedBackendError,
    cause: Throwable? = null
) : Exception(details.message, cause)

class SyncConflictException(cause: Throwable? = null) : BackendException(
    details = ParsedBackendError(
        message = SYNC_CONFLICT_MESSAGE,
        statusCode = 409
    ),
    cause = cause
)

object BackendErrorParser {
    private val gson = Gson()

    fun parse(errorBody: String?, statusCode: Int? = null): ParsedBackendError {
        if (errorBody.isNullOrBlank()) {
            return ParsedBackendError(message = null, statusCode = statusCode)
        }

        return runCatching {
            val payload = gson.fromJson(errorBody, ErrorEnvelope::class.java)
            ParsedBackendError(
                message = payload.error?.takeIf { it.isNotBlank() },
                fieldErrors = payload.fields.orEmpty().mapValuesNotNull { flattenFieldValue(it.value) },
                path = payload.path?.takeIf { it.isNotBlank() },
                errorId = payload.errorId?.takeIf { it.isNotBlank() },
                statusCode = statusCode
            )
        }.getOrElse {
            ParsedBackendError(message = errorBody.trim().takeIf { it.isNotBlank() }, statusCode = statusCode)
        }
    }

    fun fromHttpException(exception: HttpException): ParsedBackendError {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        return parse(body, exception.code())
    }

    private fun flattenFieldValue(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null

        return when {
            value.isJsonPrimitive -> value.asString.takeIf { it.isNotBlank() }
            value.isJsonArray -> value.asJsonArray
                .mapNotNull(::flattenFieldValue)
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            value.isJsonObject -> flattenJsonObject(value.asJsonObject)
            else -> value.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun flattenJsonObject(value: JsonObject): String? {
        return value.entrySet()
            .mapNotNull { (_, nestedValue) -> flattenFieldValue(nestedValue) }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
        val result = LinkedHashMap<K, R>()
        for (entry in entries) {
            transform(entry)?.let { result[entry.key] = it }
        }
        return result
    }

    private data class ErrorEnvelope(
        @SerializedName("error") val error: String? = null,
        @SerializedName("fields") val fields: Map<String, JsonElement>? = null,
        @SerializedName("path") val path: String? = null,
        @SerializedName("errorId") val errorId: String? = null
    )
}
