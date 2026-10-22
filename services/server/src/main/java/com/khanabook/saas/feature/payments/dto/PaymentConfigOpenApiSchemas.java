package com.khanabook.saas.feature.payments.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Documentation-only contracts for the backwards-compatible map payloads. */
public final class PaymentConfigOpenApiSchemas {
    private PaymentConfigOpenApiSchemas() {}

    public record UpdateRequest(@Schema(description = "Whether Easebuzz online payments are enabled") Boolean easebuzzEnabled) {}
    public record ConfigResponse(boolean subMerchantActive, boolean paymentLinkReady, boolean easebuzzEnabled,
            String subMerchantStatus, String subMerchantId, String kycStatus,
            @Schema(description = "Hosted KYC portal URL; returned only to an owner") String kycPortalUrl,
            String kycSubmittedAt, String kycActivatedAt) {}
    public record SubMerchantStatusResponse(String status, String subMerchantId, boolean hasSubMerchant, boolean isActive,
            String kycStatus, String kycSubmissionDate, @Schema(description = "Hosted KYC portal URL; returned only to an owner") String kycUrl,
            String kycExpiryDate, String bankStatus, String activationDate, String businessProof1Type, String businessProof2Type,
            String businessAddress, String legalEntityName, String tradeName, String fssaiNumber,
            boolean idProofPresent, String idProofDownloadPath, boolean bankProofPresent, String bankProofDownloadPath,
            boolean businessProof1Present, String businessProof1DownloadPath, boolean businessProof2Present, String businessProof2DownloadPath) {}
    public record OnboardingRequest(String businessName, String legalEntityName, String businessType, String restaurantFormat,
            String pan, String gst, String bankAccountNo, String ifsc, String bankName, String branchName, String beneficiaryName,
            String businessAddress, String state, String fssaiNumber, Long fssaiExpiryDate, String businessProof1Type,
            String businessProof1Url, String businessProof2Type, String businessProof2Url, String idProofUrl, String bankProofUrl,
            String contactEmail, String contactPhone, @Schema(description = "Must be zero; fees are configured by KhanaBook") BigDecimal commissionRate,
            BigDecimal upiDeductionLtLimit, BigDecimal dcDeductionGtTwoThousand) {}
    public record ActionResponse(String status, String subMerchantId, String subMerchantStatus, String kycStatus,
            String kycExpiryDate, String bankStatus, @Schema(description = "Hosted KYC portal URL; returned only to an owner") String kycUrl, String message) {}
    public record KycPortalLinkRequest(@Schema(description = "id_proof, bank_proof, business_proof_1, or business_proof_2") String docType) {}
    public record KycPortalLinkResponse(String status, String subMerchantId, String documentType, String kycUrl, String message) {}
    public record OtpVerificationRequest(@Schema(description = "OTP sent by Easebuzz") String otp) {}
    public record KycAccessKeyResponse(String status, String kycUrl, String accessKey, String message) {}
}
