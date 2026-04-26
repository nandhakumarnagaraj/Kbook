package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Easebuzz payment gateway integration.
 *
 * Three halves:
 *  - Hash + initiateTxn: builds the SHA-512 hash and posts to Easebuzz to obtain
 *    an access_key. The access_key is what the Easebuzz hosted checkout page
 *    consumes to render the payment screen.
 *  - checkoutUrl: builds the URL that the device opens (browser / Custom Tab)
 *    to let the customer actually pay.
 *  - verifyTxn: after the customer completes payment, the device asks OUR server
 *    for the authoritative status (server reads the webhook record). Never trust
 *    the device's claim of success in isolation.
 */
@Singleton
class EasebuzzClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val api: KhanaBookApi
) {

    companion object {
        // Path components only — host comes from BuildConfig.BACKEND_URL.
        // The dialog matches by path so it's host-agnostic (works in test/prod).
        const val RETURN_SUCCESS_PATH = "/payments/easebuzz/return/success"
        const val RETURN_FAILURE_PATH = "/payments/easebuzz/return/failure"
    }

    sealed class InitResult {
        data class Success(val accessKey: String, val txnId: String) : InitResult()
        data class Error(val message: String) : InitResult()
    }

    sealed class VerifyResult {
        data class Success(val txnId: String, val gatewayStatus: String) : VerifyResult()
        data class Failed(val txnId: String, val gatewayStatus: String, val reason: String?) : VerifyResult()
        data class Pending(val txnId: String) : VerifyResult()
        data class Error(val message: String) : VerifyResult()
    }

    /**
     * Hash format mandated by Easebuzz:
     *   SHA-512(key|txnid|amount|productinfo|firstname|email|||||||||||salt)
     * Eleven empty udf slots between email and salt.
     */
    fun buildRequestHash(
        key: String,
        txnId: String,
        amount: String,
        productInfo: String,
        firstName: String,
        email: String,
        salt: String
    ): String {
        val raw = listOf(key, txnId, amount, productInfo, firstName, email)
            .joinToString("|") + "|||||||||||" + salt
        return sha512(raw)
    }

    fun newTxnId(): String = "KB" + UUID.randomUUID().toString().replace("-", "").take(20)

    /**
     * URL the device opens to render the Easebuzz hosted checkout. Built from
     * the access_key returned by initiateTxn.
     */
    fun checkoutUrl(env: String, accessKey: String): String =
        if (env.equals("prod", ignoreCase = true)) {
            "https://pay.easebuzz.in/pay/$accessKey"
        } else {
            "https://testpay.easebuzz.in/pay/$accessKey"
        }

    suspend fun initiateTxn(
        profile: RestaurantProfileEntity,
        amount: String,
        productInfo: String,
        firstName: String,
        email: String,
        phone: String,
        txnId: String = newTxnId()
    ): InitResult = withContext(Dispatchers.IO) {
        val key = profile.easebuzzMerchantKey
        val salt = profile.easebuzzSalt
        if (key.isNullOrBlank() || salt.isNullOrBlank()) {
            return@withContext InitResult.Error("Easebuzz credentials not configured")
        }
        val hash = buildRequestHash(key, txnId, amount, productInfo, firstName, email, salt)

        val form = FormBody.Builder()
            .add("key", key)
            .add("txnid", txnId)
            .add("amount", amount)
            .add("productinfo", productInfo)
            .add("firstname", firstName)
            .add("email", email)
            .add("phone", phone)
            .add("hash", hash)
            .add("surl", returnUrl(RETURN_SUCCESS_PATH))
            .add("furl", returnUrl(RETURN_FAILURE_PATH))
            .build()

        val request = Request.Builder()
            .url(initiateUrl(profile.easebuzzEnv))
            .post(form)
            .build()

        try {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext InitResult.Error("HTTP ${resp.code}: $body")
                }
                val json = JSONObject(body)
                val status = json.optInt("status", 0)
                if (status == 1) {
                    val data = json.optString("data", "")
                    if (data.isNotBlank()) InitResult.Success(data, txnId)
                    else InitResult.Error("Easebuzz returned no access_key")
                } else {
                    InitResult.Error(json.optString("error_desc", "Initiation failed"))
                }
            }
        } catch (e: Exception) {
            InitResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Asks our backend for the canonical payment status. Backend reads its
     * webhook record (the trusted source) and replies with one of
     * "success" / "failed" / "pending". Anything we can't classify becomes Error.
     */
    suspend fun verifyTxn(txnId: String): VerifyResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.verifyEasebuzzTxn(txnId)
            when (resp.status.lowercase()) {
                "success" -> VerifyResult.Success(resp.txnId, resp.gatewayStatus ?: "success")
                "failed" -> VerifyResult.Failed(resp.txnId, resp.gatewayStatus ?: "failed", resp.gatewayStatus)
                "pending" -> VerifyResult.Pending(resp.txnId)
                else -> VerifyResult.Error("Unknown status: ${resp.status}")
            }
        } catch (e: Exception) {
            VerifyResult.Error(e.message ?: "Verification network error")
        }
    }

    private fun returnUrl(path: String): String =
        BuildConfig.BACKEND_URL.trimEnd('/') + path

    private fun initiateUrl(env: String): String =
        if (env.equals("prod", ignoreCase = true)) {
            "https://pay.easebuzz.in/payment/initiateLink"
        } else {
            "https://testpay.easebuzz.in/payment/initiateLink"
        }

    private fun sha512(input: String): String {
        val md = MessageDigest.getInstance("SHA-512")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
