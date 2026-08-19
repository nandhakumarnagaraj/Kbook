package com.khanabook.lite.pos.data.repository

import android.util.Log
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.EasebuzzKycAccessKeyResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingRequest
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOtpRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EasebuzzOnboardingRepository @Inject constructor(
    private val api: KhanaBookApi
) {

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

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Log.w("EasebuzzOnboardingRepo", "API call failed: ${e.message}")
        Result.failure(e)
    }
}
