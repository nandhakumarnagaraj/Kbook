package com.khanabook.lite.pos.feature.payments.data

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.payments.data.EasebuzzKycAccessKeyResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRequest
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOtpRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EasebuzzPaymentReadiness(
    val paymentLinkReady: Boolean,
    val easebuzzEnabled: Boolean,
    val agreementRequired: Boolean,
    val subMerchantStatus: String
)

@Singleton
class EasebuzzOnboardingRepository @Inject constructor(
    private val api: KhanaBookApi,
    @ApplicationContext private val context: Context
) {

    suspend fun getPaymentReadiness(): Result<EasebuzzPaymentReadiness> = runApi {
        val config = api.getEasebuzzConfig()
        val agreementRequired = config["agreementRequired"] == true
        val subMerchantStatus = config["subMerchantStatus"] as? String ?: "NOT_STARTED"
        val subMerchantId = config["subMerchantId"] as? String
        EasebuzzPaymentReadiness(
            paymentLinkReady = (config["paymentLinkReady"] as? Boolean)
                ?: (!agreementRequired && subMerchantStatus == "ACTIVE" && !subMerchantId.isNullOrBlank()),
            easebuzzEnabled = config["easebuzzEnabled"] == true,
            agreementRequired = agreementRequired,
            subMerchantStatus = subMerchantStatus
        )
    }

    suspend fun getOnboardingStatus(): Result<EasebuzzOnboardingStatusResponse> =
        runApi { api.getEasebuzzOnboardingStatus() }

    suspend fun onboard(request: EasebuzzOnboardingRequest): Result<EasebuzzOnboardingResponse> =
        runApi { api.onboardEasebuzz(request) }

    suspend fun resubmit(request: EasebuzzOnboardingRequest): Result<EasebuzzOnboardingResponse> =
        runApi { api.resubmitEasebuzz(request) }

    suspend fun generateKycAccessKey(): Result<EasebuzzKycAccessKeyResponse> =
        runApi { api.generateKycAccessKey() }

    suspend fun verifyOtp(otp: String): Result<Map<String, Any?>> =
        runApi { api.verifyEasebuzzOtp(EasebuzzOtpRequest(otp)) }

    suspend fun resendOtp(): Result<Map<String, Any?>> =
        runApi { api.resendEasebuzzOtp() }

    suspend fun lookupFssai(fssaiNo: String): Result<com.khanabook.lite.pos.feature.payments.data.FssaiLookupResponse> =
        runApi { api.lookupFssai(fssaiNo) }

    suspend fun uploadKycDocument(
        docType: String,
        file: File,
        proofType: String? = null
    ): Result<Map<String, String>> {
        return runApi {
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
            val requestBody = file.readBytes().toRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val typePart = MultipartBody.Part.createFormData("type", docType)
            val proofTypePart = proofType?.let { MultipartBody.Part.createFormData("proofType", it) }
            api.uploadKycDocument(filePart, typePart, proofTypePart)
        }
    }

    suspend fun downloadKycDocument(docType: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val body = api.downloadKycDocument(docType)
            val target = File(context.cacheDir, "kyc_${docType}_${System.currentTimeMillis()}.pdf")
            body.byteStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            Result.success(target)
        } catch (e: Exception) {
            Log.w("EasebuzzOnboardingRepo", "KYC download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Log.w("EasebuzzOnboardingRepo", "API call failed: ${e.message}")
        Result.failure(e)
    }
}
