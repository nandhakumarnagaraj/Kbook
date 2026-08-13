package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "easebuzz_sub_merchant", indexes = {
    @Index(name = "idx_sub_merchant_status", columnList = "status"),
    @Index(name = "idx_sub_merchant_kyc_status", columnList = "kyc_status")
})
@Getter
@Setter
public class EasebuzzSubMerchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "sub_merchant_id")
    private String subMerchantId;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "business_name", nullable = false)
    private String businessName;

    /**
     * Full legal entity name as registered with PAN/GST authorities.
     * EaseBuzz CPV matches the submitted business name against this; using a
     * display/trade name here causes CPV mismatch (negative report). Falls back
     * to businessName at submission time only if not separately provided.
     */
    @Column(name = "legal_entity_name")
    private String legalEntityName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "pan")
    private String pan;

    @Column(name = "gst")
    private String gst;

    @Column(name = "bank_account_no")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String bankAccountNo;

    @com.fasterxml.jackson.annotation.JsonProperty("bankAccountNo")
    public String getMaskedBankAccountNo() {
        if (bankAccountNo == null || bankAccountNo.length() < 4) {
            return bankAccountNo;
        }
        return "XXXX" + bankAccountNo.substring(bankAccountNo.length() - 4);
    }

    @Column(name = "ifsc")
    private String ifsc;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "business_address", columnDefinition = "TEXT")
    private String businessAddress;

    @Column(name = "state")
    private String state;

    /** FSSAI license number (license, not acknowledgment/application receipt). */
    @Column(name = "fssai_number")
    private String fssaiNumber;

    /** FSSAI license expiry as epoch millis. */
    @Column(name = "fssai_expiry_date")
    private Long fssaiExpiryDate;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "kyc_status")
    private String kycStatus;

    @Column(name = "kyc_portal_url")
    private String kycPortalUrl;

    @Column(name = "kyc_submitted_at")
    private Long kycSubmittedAt;

    @Column(name = "kyc_activated_at")
    private Long kycActivatedAt;

    @Column(name = "commission_rate", columnDefinition = "NUMERIC(5,2)")
    private java.math.BigDecimal commissionRate;

    @Column(name = "upi_deduction_lt_limit", columnDefinition = "NUMERIC(5,2)")
    private java.math.BigDecimal upiDeductionLtLimit;

    @Column(name = "dc_deduction_gt_two_thousand", columnDefinition = "NUMERIC(5,2)")
    private java.math.BigDecimal dcDeductionGtTwoThousand;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "split_label")
    private String splitLabel;

    @Column(name = "id_proof_url", columnDefinition = "TEXT")
    private String idProofUrl;

    @Column(name = "bank_proof_url", columnDefinition = "TEXT")
    private String bankProofUrl;

    // Business proof documents (EaseBuzz CPV). Proprietorship entities must
    // provide TWO valid business proofs; other entity types may require them
    // per risk assessment. Each slot stores the document type + uploaded URL.
    @Column(name = "business_proof_1_type", length = 100)
    private String businessProof1Type;

    @Column(name = "business_proof_1_url", columnDefinition = "TEXT")
    private String businessProof1Url;

    @Column(name = "business_proof_2_type", length = 100)
    private String businessProof2Type;

    @Column(name = "business_proof_2_url", columnDefinition = "TEXT")
    private String businessProof2Url;

    @Column(name = "easebuzz_response", columnDefinition = "TEXT")
    private String easebuzzResponse;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
