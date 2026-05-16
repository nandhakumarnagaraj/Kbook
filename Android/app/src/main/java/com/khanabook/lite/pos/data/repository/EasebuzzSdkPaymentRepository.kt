package com.khanabook.lite.pos.data.repository

import android.app.Activity
import android.content.Context
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
            deviceId = sessionManager.getDeviceId(),
            billId = localBillId
        )

    suspend fun refund(
        localBillId: Long,
        amount: java.math.BigDecimal,
        reason: String? = null
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundResponse =
        api.refundEasebuzzPayment(
            deviceId = sessionManager.getDeviceId(),
            billId = localBillId,
            request = com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest(
                amount = amount.toString(),
                reason = reason
            )
        )

    fun launchSdk(
        activity: Activity,
        accessToken: String,
        onSuccess: (String?) -> Unit,
        onFailure: (String?) -> Unit
    ) {
        // Easebuzz native SDK V2 (in.easebuzz) is not available in this build.
        // The payment flow falls back to Custom Tabs via paymentUrl.
        onFailure("SDK not bundled — use Custom Tabs fallback")
    }

    fun launchFallback(context: Context, paymentUrl: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setToolbarColor(ContextCompat.getColor(context, android.R.color.white))
            .build()
        intent.launchUrl(context, Uri.parse(paymentUrl))
    }
}
