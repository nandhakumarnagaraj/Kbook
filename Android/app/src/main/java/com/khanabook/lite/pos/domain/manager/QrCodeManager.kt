package com.khanabook.lite.pos.domain.manager


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.min

object QrCodeManager {

    private const val TAG = "QrCodeManager"
    private const val LOGO_SIZE_FRACTION = 0.22f

    /**
     * Generates a generic QR code from the given text.
     */
    fun generateQr(text: String, size: Int = 512): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val multiFormatWriter = MultiFormatWriter()
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
            val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        }
    }

    /**
     * Generates a UPI QR code from the given VPA, name, and amount.
     */
    fun generateUpiQr(vpa: String, name: String, amount: Double, size: Int = 512): Bitmap? {
        val encodedVpa = URLEncoder.encode(vpa.trim(), "UTF-8")
        val encodedName = URLEncoder.encode(name.trim(), "UTF-8")
        val encodedAmount = String.format(Locale.US, "%.2f", amount.coerceAtLeast(0.0))
        val uri = "upi://pay?pa=$encodedVpa&pn=$encodedName&am=$encodedAmount&cu=INR"
        return generateQr(uri, size)
    }

    /**
     * Generates a UPI QR code with an optional logo overlay at the center.
     * The logo is scaled to ~22% of the QR code size.
     */
    fun generateUpiQrWithLogo(
        vpa: String,
        name: String,
        amount: Double,
        logo: Bitmap? = null,
        size: Int = 512
    ): Bitmap? {
        val qr = generateUpiQr(vpa, name, amount, size) ?: return null
        if (logo == null) return qr
        return try {
            val result = qr.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val logoSize = (size * LOGO_SIZE_FRACTION).toInt().coerceIn(32, size / 3)
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
            val left = (size - logoSize) / 2f
            val top = (size - logoSize) / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(
                RectF(left - 4f, top - 4f, left + logoSize + 4f, top + logoSize + 4f),
                8f, 8f, paint.apply { color = android.graphics.Color.WHITE }
            )
            canvas.drawBitmap(scaledLogo, left, top, null)
            scaledLogo.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to overlay logo on QR code", e)
            qr
        }
    }
}
