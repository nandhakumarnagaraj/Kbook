package com.khanabook.lite.pos.domain.manager


import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

object QrCodeManager {

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
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a UPI QR code from the given VPA, name, and amount.
     */
    fun generateUpiQr(vpa: String, name: String, amount: Double, size: Int = 512): Bitmap? {
        val uri = "upi://pay?pa=$vpa&pn=$name&am=${"%.2f".format(amount)}&cu=INR"
        return generateQr(uri, size)
    }
}
