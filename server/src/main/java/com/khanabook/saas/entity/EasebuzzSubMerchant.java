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

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "pan")
    private String pan;

    @Column(name = "gst")
    private String gst;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "ifsc")
    private String ifsc;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "business_address", columnDefinition = "TEXT")
    private String businessAddress;

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

    @Column(name = "easebuzz_response", columnDefinition = "JSONB")
    private String easebuzzResponse;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
