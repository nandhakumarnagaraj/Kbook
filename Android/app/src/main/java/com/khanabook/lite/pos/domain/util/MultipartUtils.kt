package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

object MultipartUtils {
    private const val MAX_ASSET_BYTES = 2L * 1024L * 1024L

    fun imageUriToPart(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part {
        val resolver = context.contentResolver
        val size = querySize(context, uri)
        if (size != null && size > MAX_ASSET_BYTES) {
            throw IllegalArgumentException("Image must be 2 MB or smaller.")
        }

        val mimeType = resolver.getType(uri) ?: "image/*"
        val fileName = queryDisplayName(context, uri) ?: "asset_${System.currentTimeMillis()}"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("Unable to read selected image.")

        if (bytes.size > MAX_ASSET_BYTES) {
            throw IllegalArgumentException("Image must be 2 MB or smaller.")
        }

        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, fileName, body)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return queryOpenableColumn(context, uri, OpenableColumns.DISPLAY_NAME)
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return queryOpenableColumn(context, uri, OpenableColumns.SIZE)?.toLongOrNull()
    }

    private fun queryOpenableColumn(context: Context, uri: Uri, column: String): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(column), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(column)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
