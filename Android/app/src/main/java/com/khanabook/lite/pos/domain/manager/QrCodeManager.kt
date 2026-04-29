package com.khanabook.lite.pos.domain.manager


import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.URLEncoder
import java.util.Locale

object QrCodeManager {

    private const val TAG = "QrCodeManager"

    /**
     * Generates a generic QR code from the given text.
     */
    fun generateQr(text: String, size: Int = 512): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, size, size)
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
}
