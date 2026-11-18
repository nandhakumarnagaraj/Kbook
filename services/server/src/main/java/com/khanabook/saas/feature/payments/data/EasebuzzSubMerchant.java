package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated Retired in V129. Easebuzz sub-merchants retired.
 */
@Deprecated
@Getter
@Setter
public class EasebuzzSubMerchant {

    private Long id;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long restaurantId;
    private String subMerchantId;
    private String status = "DRAFT";
    private String businessName;
    private String legalEntityName;
    private String businessType;
    private String pan;
    private String gst;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String bankAccountNo;

    @com.fasterxml.jackson.annotation.JsonProperty("bankAccountNo")
    public String getMaskedBankAccountNo() {
        if (bankAccountNo == null || bankAccountNo.length() < 4) {
            return bankAccountNo;
        }
        return "XXXX" + bankAccountNo.substring(bankAccountNo.length() - 4);
    }

    private String ifsc;
    private String beneficiaryName;
    private String businessAddress;
    private String state;
    private String fssaiNumber;
    private Long fssaiExpiryDate;
    private String contactEmail;
    private String contactPhone;
    private String kycStatus;
    private String kycPortalUrl;
    private Boolean bankStatus;
    private String kycExpiryDate;
    private Long kycSubmittedAt;
    private Long kycActivatedAt;
    private java.math.BigDecimal commissionRate;
    private java.math.BigDecimal upiDeductionLtLimit;
    private java.math.BigDecimal dcDeductionGtTwoThousand;
    private String bankName;
    private String branchName;
    private String splitLabel;
    private Integer splitLabelVersion = 0;
    private String restaurantFormat;
    private String splitLabelBankSnapshot;
    private String idProofUrl;
    private String bankProofUrl;
    private String businessProof1Type;
    private String businessProof1Url;
    private String businessProof2Type;
    private String businessProof2Url;
    private String idProofKey;
    private String bankProofKey;
    private String businessProof1Key;
    private String businessProof2Key;
    private String portalPassword;
    private String easebuzzResponse;
    private String virtualAccountId;
    private String virtualAccountNumber;
    private String virtualAccountIfsc;
    private String virtualAccountBank;
    private Long createdAt;
    private Long updatedAt;
}
