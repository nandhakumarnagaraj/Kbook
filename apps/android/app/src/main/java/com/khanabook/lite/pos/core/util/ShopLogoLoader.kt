package com.khanabook.lite.pos.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.khanabook.lite.pos.R

object ShopLogoLoader {

    /**
     * Loads the restaurant shop logo bitmap.
     * Tries remote logoUrl first (via Coil memory/disk cache), then local stored asset logoPath.
     * If neither is present or fails, optionally falls back to the app's brand logo.
     */
    suspend fun loadShopLogo(
        context: Context,
        logoUrl: String?,
        logoPath: String?,
        fallbackToDefault: Boolean = true
    ): Bitmap? {
        if (!logoUrl.isNullOrBlank()) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .allowHardware(false)
                    .size(128)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
                if (bitmap != null) return bitmap
            } catch (_: Exception) { }
        }

        AppAssetStore.resolveAssetPath(logoPath)?.let { path ->
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) return bitmap
            } catch (_: Exception) { }
        }

        if (fallbackToDefault) {
            try {
                return BitmapFactory.decodeResource(context.resources, R.drawable.khanabook_logo)
            } catch (_: Exception) { }
        }

        return null
    }
}
