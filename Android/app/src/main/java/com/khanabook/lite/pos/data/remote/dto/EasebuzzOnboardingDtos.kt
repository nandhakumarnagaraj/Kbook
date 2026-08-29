package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EasebuzzOnboardingRequest(
    @SerializedName("businessName") val businessName: String,
    @SerializedName("legalEntityName") val legalEntityName: String?,
    @SerializedName("businessType") val businessType: String,
    @SerializedName("pan") val pan: String,
    @SerializedName("gst") val gst: String?,
    @SerializedName("businessAddress") val businessAddress: String,
    @SerializedName("state") val state: String,
    @SerializedName("contactEmail") val contactEmail: String,
    @SerializedName("contactPhone") val contactPhone: String,
    @SerializedName("bankAccountNo") val bankAccountNo: String,
    @SerializedName("ifsc") val ifsc: String,
    @SerializedName("bankName") val bankName: String,
    @SerializedName("branchName") val branchName: String,
    @SerializedName("beneficiaryName") val beneficiaryName: String,
    @SerializedName("fssaiNumber") val fssaiNumber: String? = null
)

data class EasebuzzOnboardingResponse(
    @SerializedName("status") val status: String,
    @SerializedName("subMerchantId") val subMerchantId: String? = null,
    @SerializedName("subMerchantStatus") val subMerchantStatus: String? = null,
    @SerializedName("kycStatus") val kycStatus: String? = null,
    @SerializedName("message") val message: String? = null
)

data class EasebuzzOnboardingStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("subMerchantId") val subMerchantId: String? = null,
    @SerializedName("hasSubMerchant") val hasSubMerchant: Boolean = false,
    @SerializedName("isActive") val isActive: Boolean = false,
    @SerializedName("kycStatus") val kycStatus: String? = null,
    @SerializedName("kycSubmissionDate") val kycSubmissionDate: String? = null,
    @SerializedName("kycUrl") val kycUrl: String? = null,
    @SerializedName("activationDate") val activationDate: String? = null,
    @SerializedName("idProofPresent") val idProofPresent: Boolean = false,
    @SerializedName("idProofDownloadPath") val idProofDownloadPath: String? = null,
    @SerializedName("bankProofPresent") val bankProofPresent: Boolean = false,
    @SerializedName("bankProofDownloadPath") val bankProofDownloadPath: String? = null,
    @SerializedName("businessProof1Present") val businessProof1Present: Boolean = false,
    @SerializedName("businessProof1DownloadPath") val businessProof1DownloadPath: String? = null,
    @SerializedName("businessProof2Present") val businessProof2Present: Boolean = false,
    @SerializedName("businessProof2DownloadPath") val businessProof2DownloadPath: String? = null
)

data class EasebuzzOtpRequest(
    @SerializedName("otp") val otp: String
)

data class EasebuzzKycAccessKeyResponse(
    @SerializedName("status") val status: Any? = null,
    @SerializedName("kyc_url") val kycUrl: String? = null,
    @SerializedName("sub_merchant_id") val subMerchantId: String? = null
)
