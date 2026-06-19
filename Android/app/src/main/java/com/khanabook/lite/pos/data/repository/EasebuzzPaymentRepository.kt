package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderRequest
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzPaymentStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzVerifyResponse
import com.khanabook.lite.pos.domain.manager.SessionManager
import java.math.BigDecimal

class EasebuzzPaymentRepository(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
    suspend fun createOrder(
        restaurantId: Long,
        localBillId: Long,
        paymentMethod: String = "ONLINE",
        gatewayAmount: BigDecimal? = null
    ): CreateEasebuzzOrderResponse =
        api.createEasebuzzOrder(
            deviceId = sessionManager.getDeviceId(),
            request = CreateEasebuzzOrderRequest(
                billId = localBillId,
                paymentMethod = paymentMethod,
                gatewayAmount = gatewayAmount,
                restaurantId = restaurantId
            )
        )

    suspend fun getStatus(
        localBillId: Long,
        refresh: Boolean = false
    ): EasebuzzPaymentStatusResponse =
        api.getEasebuzzPaymentStatus(
            deviceId = sessionManager.getDeviceId(),
            billId = localBillId,
            refresh = refresh
        )

    suspend fun verify(localBillId: Long): EasebuzzVerifyResponse =
        api.verifyEasebuzzPayment(
            billId = localBillId,
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun getSubMerchantStatus(): EasebuzzSubMerchantStatusResponse =
        api.getEasebuzzSubMerchantStatus(
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun onboardSubMerchant(
        request: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardRequest
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantActionResponse =
        api.onboardEasebuzzSubMerchant(
            deviceId = sessionManager.getDeviceId(),
            request = request
        )

    suspend fun resubmitSubMerchant(
        request: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardRequest
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantActionResponse =
        api.resubmitEasebuzzSubMerchant(
            deviceId = sessionManager.getDeviceId(),
            request = request
        )

    suspend fun generateKycAccessKey(): com.khanabook.lite.pos.data.remote.dto.EasebuzzKycAccessKeyResponse =
        api.generateEasebuzzKycAccessKey(
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun verifyKycOtp(otp: String): com.khanabook.lite.pos.data.remote.dto.EasebuzzOtpResponse =
        api.verifyEasebuzzKycOtp(
            deviceId = sessionManager.getDeviceId(),
            request = com.khanabook.lite.pos.data.remote.dto.EasebuzzOtpRequest(otp = otp)
        )

    suspend fun resendKycOtp(): com.khanabook.lite.pos.data.remote.dto.EasebuzzOtpResponse =
        api.resendEasebuzzKycOtp(
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun refund(
        localBillId: Long,
        amount: java.math.BigDecimal,
        reason: String? = null
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundResponse =
        api.refundEasebuzzPayment(
            billId = localBillId,
            deviceId = sessionManager.getDeviceId(),
            request = com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest(
                amount = amount.toString(),
                reason = reason
            )
        )

    suspend fun uploadKycDocument(
        docType: String,
        fileUri: android.net.Uri,
        context: android.content.Context
    ): com.khanabook.lite.pos.data.remote.api.KycDocumentUploadResponse {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(fileUri) ?: throw java.io.IOException("Unable to open stream")
        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        
        var filename = "kyc_doc"
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                filename = cursor.getString(nameIndex)
            }
        }

        val bytes = inputStream.use { it.readBytes() }
        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val body = okhttp3.MultipartBody.Part.createFormData("file", filename, requestFile)
        
        return api.uploadKycDocument(docType, body)
    }

    suspend fun createFssaiOrder(
        restaurantId: Long,
        years: Int,
        fssaiNumber: String
    ): Map<String, Any> =
        api.createFssaiOrder(
            deviceId = sessionManager.getDeviceId(),
            request = mapOf(
                "restaurantId" to restaurantId,
                "years" to years,
                "fssaiNumber" to fssaiNumber
            )
        )
}
