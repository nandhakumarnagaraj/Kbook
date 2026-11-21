package com.khanabook.lite.pos.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Display metadata for a logo picked from the gallery, shown after a confirmed upload. */
data class LogoFileInfo(val fileName: String, val fileSizeBytes: Long) {

    companion object {
        fun from(context: Context, uri: Uri): LogoFileInfo {
            var name: String? = null
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Some providers do not expose OpenableColumns; the fallback name is enough.
            }
            return LogoFileInfo(name?.takeIf { it.isNotBlank() } ?: "shop_logo", size)
        }
    }
}
