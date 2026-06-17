package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CreateEasebuzzOrderRequest(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("gatewayAmount") val gatewayAmount: BigDecimal? = null,
    @SerializedName("restaurantId") val restaurantId: Long
)

data class CreateEasebuzzOrderResponse(
    @SerializedName("status") val status: String,
    @SerializedName("txnid") val txnid: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("payment_url") val paymentUrl: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("pay_mode") val payMode: String = "test"
)

data class EasebuzzPaymentStatusResponse(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentId") val paymentId: Long,
    @SerializedName("paymentStatus") val paymentStatus: String,
    @SerializedName("gatewayTxnId") val gatewayTxnId: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("message") val message: String
)

data class EasebuzzVerifyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("easebuzz_id") val easebuzzId: String,
    @SerializedName("txnid") val txnid: String
)

data class EasebuzzSubMerchantStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("subMerchantId") val subMerchantId: String? = null,
    @SerializedName("hasSubMerchant") val hasSubMerchant: Boolean = false,
    @SerializedName("isActive") val isActive: Boolean = false,
    @SerializedName("kycUrl") val kycUrl: String? = null,
    @SerializedName("kycStatus") val kycStatus: String? = null,
    @SerializedName("kycSubmissionDate") val kycSubmissionDate: String? = null,
    @SerializedName("activationDate") val activationDate: String? = null,
    @SerializedName("idProofUrl") val idProofUrl: String? = null,
    @SerializedName("bankProofUrl") val bankProofUrl: String? = null,
    @SerializedName("businessProof1Url") val businessProof1Url: String? = null,
    @SerializedName("businessProof2Url") val businessProof2Url: String? = null
)

data class EasebuzzRefundRequest(
    @SerializedName("amount") val amount: String,
    @SerializedName("reason") val reason: String? = null
)

data class EasebuzzRefundResponse(
    @SerializedName("status") val status: String,
    @SerializedName("easebuzz_refund_id") val easebuzzRefundId: String? = null,
    @SerializedName("error") val error: String? = null
)

data class EasebuzzRefundStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("refund_id") val refundId: String? = null,
    @SerializedName("txnid") val txnid: String? = null,
    @SerializedName("refund_status") val refundStatus: String? = null,
    @SerializedName("error") val error: String? = null
)

// ── Sub-merchant onboarding & KYC (owner-driven, POS app) ────────────────────

/**
 * Onboarding details the owner submits to register as an Easebuzz sub-merchant.
 * Documents are NOT sent here — Easebuzz has no document API; they are uploaded
 * later on Easebuzz's hosted KYC portal. FSSAI is mandatory for food merchants.
 */
data class EasebuzzOnboardRequest(
    @SerializedName("businessName") val businessName: String,
    @SerializedName("legalEntityName") val legalEntityName: String,
    @SerializedName("businessType") val businessType: String,
    @SerializedName("pan") val pan: String? = null,
    @SerializedName("gst") val gst: String? = null,
    @SerializedName("fssaiNumber") val fssaiNumber: String,
    @SerializedName("fssaiExpiryDate") val fssaiExpiryDate: Long? = null,
    @SerializedName("businessAddress") val businessAddress: String,
    @SerializedName("state") val state: String,
    @SerializedName("bankAccountNo") val bankAccountNo: String,
    @SerializedName("ifsc") val ifsc: String,
    @SerializedName("bankName") val bankName: String,
    @SerializedName("branchName") val branchName: String? = null,
    @SerializedName("beneficiaryName") val beneficiaryName: String,
    @SerializedName("contactEmail") val contactEmail: String,
    @SerializedName("contactPhone") val contactPhone: String
)

/** Result of an onboarding / resubmit action. */
data class EasebuzzSubMerchantActionResponse(
    @SerializedName("status") val status: String,
    @SerializedName("subMerchantId") val subMerchantId: String? = null,
    @SerializedName("subMerchantStatus") val subMerchantStatus: String? = null,
    @SerializedName("kycStatus") val kycStatus: String? = null,
    @SerializedName("message") val message: String? = null
)

/** Result of generating the Easebuzz hosted KYC portal URL. */
data class EasebuzzKycAccessKeyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("kyc_url") val kycUrl: String? = null,
    @SerializedName("sub_merchant_id") val subMerchantId: String? = null
)

data class EasebuzzOtpRequest(
    @SerializedName("otp") val otp: String
)

/** Raw OTP/action response from Easebuzz (shape varies; surfaced as-is). */
data class EasebuzzOtpResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("error") val error: String? = null
)
