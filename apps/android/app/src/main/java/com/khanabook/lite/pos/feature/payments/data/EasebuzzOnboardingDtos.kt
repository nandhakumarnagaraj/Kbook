package com.khanabook.lite.pos.feature.payments.data

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
    @SerializedName("businessProof1Type") val businessProof1Type: String? = null,
    @SerializedName("businessProof2Present") val businessProof2Present: Boolean = false,
    @SerializedName("businessProof2DownloadPath") val businessProof2DownloadPath: String? = null,
    @SerializedName("businessProof2Type") val businessProof2Type: String? = null,
    @SerializedName("businessAddress") val businessAddress: String? = null,
    @SerializedName("legalEntityName") val legalEntityName: String? = null,
    @SerializedName("tradeName") val tradeName: String? = null,
    @SerializedName("fssaiNumber") val fssaiNumber: String? = null
)

data class EasebuzzOtpRequest(
    @SerializedName("otp") val otp: String
)

data class EasebuzzKycAccessKeyResponse(
    @SerializedName("status") val status: Any? = null,
    @SerializedName("kyc_url") val kycUrl: String? = null,
    @SerializedName("sub_merchant_id") val subMerchantId: String? = null
)

data class FssaiLookupResponse(
    @SerializedName("valid") val valid: Boolean = false,
    @SerializedName("businessName") val businessName: String? = null,
    @SerializedName("legalEntityName") val legalEntityName: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("pincode") val pincode: String? = null,
    @SerializedName("pan") val pan: String? = null,
    @SerializedName("contactPerson") val contactPerson: String? = null,
    @SerializedName("contactEmail") val contactEmail: String? = null,
    @SerializedName("licenseNo") val licenseNo: String? = null,
    @SerializedName("expiryDate") val expiryDate: String? = null,
    @SerializedName("error") val error: String? = null
)

enum class AddressProofType(
    val code: String,
    val label: String,
    val description: String
) {
    ELECTRICITY_BILL(
        "ELECTRICITY_BILL",
        "Electricity Bill",
        "Recent bill (last 2-3 months) in business or proprietor name"
    ),
    GST_CERTIFICATE(
        "GST_CERTIFICATE",
        "GST Certificate (REG-06)",
        "GST registration showing principal place of business"
    ),
    SHOP_ESTABLISHMENT(
        "SHOP_ESTABLISHMENT",
        "Shop & Establishment / Gumasta",
        "Municipal trade license / Shop Act registration certificate"
    ),
    RENT_AGREEMENT(
        "RENT_AGREEMENT",
        "Rent / Lease Agreement",
        "Registered rental or lease deed for the shop premises"
    ),
    FSSAI_LICENSE(
        "FSSAI_LICENSE",
        "FSSAI Food License",
        "FSSAI Registration or State/Central License certificate"
    ),
    UDYAM_MSME(
        "UDYAM_MSME",
        "Udyam / MSME Certificate",
        "Government MSME / Udyam registration showing plant/unit address"
    ),
    TRADE_LICENSE(
        "TRADE_LICENSE",
        "Trade License / Municipal Tax",
        "Local municipal corporation trade license or property tax receipt"
    );

    companion object {
        fun fromCode(code: String?): AddressProofType? {
            if (code == null) return null
            return entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
        }
    }
}
