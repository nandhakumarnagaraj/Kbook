package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.remote.api.InitiateRefundRequest
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Easebuzz payment gateway integration.
 *
 * Android never holds merchant secrets. It asks our backend to create a
 * backend-owned Easebuzz order and only receives a checkoutUrl + transaction id.
 * Final status always comes from the backend webhook-derived payment state.
 */
@Singleton
class EasebuzzClient @Inject constructor(
    private val api: KhanaBookApi
) {

    companion object {
        const val RETURN_SUCCESS_PATH = "/payments/easebuzz/return/success"
        const val RETURN_FAILURE_PATH = "/payments/easebuzz/return/failure"
    }

    sealed class InitResult {
        data class Success(
            val paymentId: Long,
            val billId: Long,
            val checkoutUrl: String,
            val txnId: String
        ) : InitResult()
        data class Error(val message: String) : InitResult()
    }

    sealed class RefundResult {
        object Success : RefundResult()
        data class Error(val message: String) : RefundResult()
    }

    sealed class VerifyResult {
        data class Success(val paymentId: Long, val billId: Long, val txnId: String, val message: String) : VerifyResult()
        data class Failed(val paymentId: Long, val billId: Long, val txnId: String, val reason: String?) : VerifyResult()
        data class Pending(val paymentId: Long?, val billId: Long, val txnId: String?) : VerifyResult()
        data class Error(val message: String) : VerifyResult()
    }

    suspend fun initiateTxn(
        billId: Long,
        paymentMethod: String,
        gatewayAmount: String? = null
    ): InitResult = withContext(Dispatchers.IO) {
        try {
            val response = api.createEasebuzzOrder(
                com.khanabook.lite.pos.data.remote.api.CreateEasebuzzOrderRequest(
                    billId = billId,
                    paymentMethod = paymentMethod,
                    gatewayAmount = gatewayAmount
                )
            )
            InitResult.Success(
                paymentId = response.paymentId,
                billId = response.billId,
                checkoutUrl = response.checkoutUrl,
                txnId = response.gatewayTxnId
            )
        } catch (e: Exception) {
            InitResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun initiateRefund(serverBillId: Long, amount: String, reason: String): RefundResult =
        withContext(Dispatchers.IO) {
            try {
                api.initiateEasebuzzRefund(serverBillId, InitiateRefundRequest(amount, reason))
                RefundResult.Success
            } catch (e: Exception) {
                RefundResult.Error(e.message ?: "Refund request failed")
            }
        }

    suspend fun verifyBill(billId: Long): VerifyResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.getEasebuzzPaymentStatus(billId)
            when (resp.paymentStatus.uppercase()) {
                "SUCCESS" -> VerifyResult.Success(resp.paymentId, resp.billId, resp.gatewayTxnId, resp.message)
                "FAILED", "CANCELLED" -> VerifyResult.Failed(resp.paymentId, resp.billId, resp.gatewayTxnId, resp.message)
                "PENDING" -> VerifyResult.Pending(resp.paymentId, resp.billId, resp.gatewayTxnId)
                else -> VerifyResult.Error("Unknown status: ${resp.paymentStatus}")
            }
        } catch (e: Exception) {
            VerifyResult.Error(e.message ?: "Verification network error")
        }
    }
}
