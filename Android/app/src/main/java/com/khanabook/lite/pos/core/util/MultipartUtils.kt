package com.khanabook.lite.pos.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

object MultipartUtils {
    /** Maximum bytes accepted from the picker before decoding (matches server multipart cap). */
    private const val MAX_INPUT_BYTES = 10L * 1024L * 1024L
    /** Maximum bytes sent over the network and accepted by AssetStorageService. */
    private const val MAX_UPLOAD_BYTES = 2L * 1024L * 1024L
    private const val MIN_DIMENSION_PX = 64
    private const val MAX_DIMENSION_PX = 4096
    private const val MAX_ASPECT_RATIO = 10f
    private const val OUTPUT_MAX_EDGE_PX = 1024
    private const val WEBP_QUALITY = 90

    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Validates, downscales, and re-encodes a picked image into an upload part.
     *
     * Defense layers applied before anything leaves the device:
     * 1. Size cap on actual stream bytes (provider-reported length is not trusted).
     * 2. Magic-byte whitelist (PNG / JPEG / WebP only — rejects SVG/GIF/etc.
     *    even when the provider reports a lying MIME type).
     * 3. Full decode — proves the bytes are a real raster image.
     * 4. Dimension caps + aspect-ratio sanity.
     * 5. Re-encode to WebP capped at [OUTPUT_MAX_EDGE_PX] — strips any payload
     *    embedded in the original container and caps decode cost downstream
     *    (invoice PDF, thermal print, UPI QR overlay).
     */
    fun imageUriToPart(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("Unable to read the selected image.")

        if (bytes.isEmpty()) throw IllegalArgumentException("The selected file is empty.")
        if (bytes.size > MAX_INPUT_BYTES) {
            throw IllegalArgumentException("Image must be 10 MB or smaller.")
        }

        requireSupportedFormat(bytes)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("This file is not a valid PNG, JPG, or WebP image.")
        }
        if (bounds.outWidth < MIN_DIMENSION_PX || bounds.outHeight < MIN_DIMENSION_PX) {
            throw IllegalArgumentException("Image is too small. Use at least ${MIN_DIMENSION_PX}x${MIN_DIMENSION_PX}px.")
        }
        if (bounds.outWidth > MAX_DIMENSION_PX && bounds.outHeight > MAX_DIMENSION_PX) {
            throw IllegalArgumentException("Image is too large. Use at most ${MAX_DIMENSION_PX}x${MAX_DIMENSION_PX}px.")
        }
        val wider = maxOf(bounds.outWidth, bounds.outHeight).toFloat()
        val narrower = minOf(bounds.outWidth, bounds.outHeight).toFloat()
        if (wider / narrower > MAX_ASPECT_RATIO) {
            throw IllegalArgumentException("Image is too stretched. Pick something closer to square.")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("This file is not a valid PNG, JPG, or WebP image.")

        val scaled = scaleToMaxEdge(decoded, OUTPUT_MAX_EDGE_PX)
        val compressed = encodeWebpWithinLimit(scaled)
        if (scaled !== decoded) {
            decoded.recycle()
            scaled.recycle()
        }

        val body = compressed.toRequestBody("image/webp".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, "shop_logo_${System.currentTimeMillis()}.webp", body)
    }

    private fun requireSupportedFormat(bytes: ByteArray) {
        val ok = when {
            bytes.size >= PNG_MAGIC.size && bytes.copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC) -> true
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> true
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF"
                && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> true
            else -> false
        }
        if (!ok) {
            throw IllegalArgumentException("Only PNG, JPG, or WebP images are allowed.")
        }
    }

    private fun computeSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var maxEdge = maxOf(width, height)
        while (maxEdge / 2 >= OUTPUT_MAX_EDGE_PX) {
            sample *= 2
            maxEdge /= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= maxEdgePx) return bitmap
        val scale = maxEdgePx.toFloat() / maxEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Encode to WebP while keeping the multipart payload within the server's CDN cap.
     * Most 1024px food photos fit at quality 90; noisier photos progressively step down
     * rather than failing after the user already picked a valid image.
     */
    private fun encodeWebpWithinLimit(bitmap: Bitmap): ByteArray {
        val qualities = intArrayOf(WEBP_QUALITY, 82, 74, 66)
        for (quality in qualities) {
            val encoded = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
                out.toByteArray()
            }
            if (encoded.size <= MAX_UPLOAD_BYTES) return encoded
        }
        throw IllegalArgumentException(
            "This image could not be compressed below 2 MB. Try a less detailed photo."
        )
    }
}
