package com.khanabook.lite.pos.data.repository

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class MerchantAgreementRepository @Inject constructor(
    private val api: KhanaBookApi,
    @ApplicationContext private val context: Context
) {
    suspend fun getStatus(): Result<Map<String, Any?>> = runApi { api.getMerchantAgreementStatus() }

    suspend fun upload(signerName: String, agreementVersion: String, file: File): Result<Map<String, Any?>> {
        return runApi {
            val requestBody = file.readBytes().toRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val namePart = MultipartBody.Part.createFormData("signerName", signerName)
            val versionPart = MultipartBody.Part.createFormData("agreementVersion", agreementVersion)
            api.uploadMerchantAgreement(filePart, namePart, versionPart)
        }
    }

    suspend fun download(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val body = api.downloadMerchantAgreement()
            val target = File(context.cacheDir, "merchant_agreement_${System.currentTimeMillis()}.pdf")
            body.byteStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            Result.success(target)
        } catch (e: Exception) {
            Log.w("MerchantAgreementRepo", "Download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Log.w("MerchantAgreementRepo", "API call failed: ${e.message}")
        Result.failure(e)
    }
}
