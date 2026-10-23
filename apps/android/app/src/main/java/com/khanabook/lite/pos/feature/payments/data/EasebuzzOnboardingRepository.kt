package com.khanabook.lite.pos.feature.payments.data

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.payments.data.EasebuzzKycAccessKeyResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRequest
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EasebuzzPaymentReadiness(
    val paymentLinkReady: Boolean,
    val easebuzzEnabled: Boolean,
    val subMerchantActive: Boolean,
    val subMerchantStatus: String
)

@Singleton
class EasebuzzOnboardingRepository @Inject constructor(
    private val api: KhanaBookApi,
    @ApplicationContext private val context: Context
) {

    suspend fun getPaymentReadiness(): Result<EasebuzzPaymentReadiness> = runApi {
        val config = api.getEasebuzzConfig()
        val subMerchantStatus = config["subMerchantStatus"] as? String ?: "NOT_STARTED"
        val subMerchantId = config["subMerchantId"] as? String
        val subMerchantActive = (config["subMerchantActive"] as? Boolean)
            ?: (subMerchantStatus == "ACTIVE" && !subMerchantId.isNullOrBlank())
        EasebuzzPaymentReadiness(
            paymentLinkReady = (config["paymentLinkReady"] as? Boolean)
                ?: (subMerchantActive && config["easebuzzEnabled"] == true),
            easebuzzEnabled = config["easebuzzEnabled"] == true,
            subMerchantActive = subMerchantActive,
            subMerchantStatus = subMerchantStatus
        )
    }

    suspend fun getOnboardingStatus(): Result<EasebuzzOnboardingStatusResponse> =
        runApi { api.getEasebuzzOnboardingStatus() }

    suspend fun onboard(request: EasebuzzOnboardingRequest): Result<EasebuzzOnboardingResponse> =
        runApi { api.onboardEasebuzz(request) }

    /**
     * Correct the details of a sub-merchant that already exists at Easebuzz. The server keeps a
     * rejected merchant in REJECTED and rejects /onboard with ONBOARDING_ALREADY_SUBMITTED, so
     * this is the only endpoint that can push corrected details after a CPV negative report.
     */
    suspend fun resubmit(request: EasebuzzOnboardingRequest): Result<EasebuzzOnboardingResponse> =
        runApi { api.resubmitEasebuzz(request) }

    suspend fun generateKycAccessKey(): Result<EasebuzzKycAccessKeyResponse> =
        runApi { api.generateKycAccessKey() }

    /**
     * The Easebuzz dashboard login issued at creation. Owner-only on the server, and
     * `retained=false` means there is no stored copy to show.
     */
    suspend fun portalCredentials(): Result<EasebuzzPortalCredentials> =
        runApi { api.getEasebuzzPortalCredentials() }

    suspend fun lookupFssai(fssaiNo: String): Result<com.khanabook.lite.pos.feature.payments.data.FssaiLookupResponse> =
        runApi { api.lookupFssai(fssaiNo) }

    /**
     * Asks the server where to upload [docType] and returns the portal link.
     *
     * No file is sent: Easebuzz has no document-ingest API, so the server could only validate
     * and discard the bytes. Sending them meant buffering an entire document in memory and
     * spending the terminal's bandwidth on an upload that could never succeed.
     */
    suspend fun requestKycPortalLink(
        docType: String,
        proofType: String? = null
    ): Result<Map<String, String>> = runApi {
        api.requestKycPortalLink(
            buildMap {
                put("docType", docType)
                if (!proofType.isNullOrBlank()) put("proofType", proofType)
            }
        )
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
