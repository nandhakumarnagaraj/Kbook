package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream

object AppAssetStore {
    private const val ASSET_ROOT = "assets"
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun saveUrlToAppAsset(
        url: String,
        folder: String,
        fileName: String
    ): String? {
        check(::appContext.isInitialized) { "AppAssetStore is not initialized." }
        return try {
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .allowHardware(false)
                .build()
            val result = appContext.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return null

            val assetRef = "$folder/$fileName"
            val file = File(appContext.filesDir, "$ASSET_ROOT/$assetRef")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            assetRef
        } catch (_: Exception) {
            null
        }
    }

    fun saveUriToAppAsset(
        context: Context,
        uri: Uri,
        folder: String,
        fileName: String
    ): String? {
        return try {
            val assetRef = "$folder/$fileName"
            val file = File(context.filesDir, "$ASSET_ROOT/$assetRef")
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            assetRef
        } catch (_: Exception) {
            null
        }
    }

    fun resolveAssetPath(storedPath: String?): String? {
        if (storedPath.isNullOrBlank()) return null
        if (storedPath.startsWith("content://")) return storedPath
        if (File(storedPath).isAbsolute) return storedPath
        check(::appContext.isInitialized) { "AppAssetStore is not initialized." }
        return File(appContext.filesDir, "$ASSET_ROOT/$storedPath").absolutePath
    }
}
