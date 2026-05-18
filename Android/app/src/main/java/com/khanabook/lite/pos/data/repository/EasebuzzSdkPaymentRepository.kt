package com.khanabook.lite.pos.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderRequest
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzPaymentStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzVerifyResponse
import com.khanabook.lite.pos.domain.manager.SessionManager
import java.math.BigDecimal

class EasebuzzSdkPaymentRepository(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
    suspend fun createOrder(
        restaurantId: Long,
        serverBillId: Long,
        paymentMethod: String = "ONLINE",
        gatewayAmount: BigDecimal? = null
    ): CreateEasebuzzOrderResponse =
        api.createEasebuzzOrder(
            deviceId = sessionManager.getDeviceId(),
            request = CreateEasebuzzOrderRequest(
                billId = serverBillId,
                paymentMethod = paymentMethod,
                gatewayAmount = gatewayAmount,
                restaurantId = restaurantId
            )
        )

    suspend fun getStatus(
        serverBillId: Long,
        refresh: Boolean = false
    ): EasebuzzPaymentStatusResponse =
        api.getEasebuzzPaymentStatus(
            deviceId = sessionManager.getDeviceId(),
            billId = serverBillId,
            refresh = refresh
        )

    suspend fun verify(serverBillId: Long): EasebuzzVerifyResponse =
        api.verifyEasebuzzPayment(
            deviceId = sessionManager.getDeviceId(),
            billId = serverBillId
        )

    suspend fun refund(
        serverBillId: Long,
        amount: java.math.BigDecimal,
        reason: String? = null
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundResponse =
        api.refundEasebuzzPayment(
            deviceId = sessionManager.getDeviceId(),
            billId = serverBillId,
            request = com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest(
                amount = amount.toString(),
                reason = reason
            )
        )

    suspend fun getRefundStatus(
        serverBillId: Long
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundStatusResponse =
        api.getEasebuzzRefundStatus(
            deviceId = sessionManager.getDeviceId(),
            billId = serverBillId
        )

    /**
     * Creates an Intent to launch the native Easebuzz SDK checkout activity (PWECheckoutActivity).
     * Uses the host app's package name since the SDK AAR's activity is merged into the host manifest.
     * The calling composable should use ActivityResultLauncher to start it
     * and handle the result via payment verification.
     */
    fun createSdkIntent(accessToken: String, context: Context): Intent {
        return Intent().apply {
            setClassName(context.packageName, "com.easebuzz.payment.kit.PWECheckoutActivity")
            putExtra("access_key", accessToken)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchFallback(context: Context, paymentUrl: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setToolbarColor(ContextCompat.getColor(context, android.R.color.white))
            .build()
        intent.launchUrl(context, Uri.parse(paymentUrl))
    }
}
