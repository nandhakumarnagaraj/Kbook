package com.khanabook.saas.feature.restaurants.data;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@Data
public class RestaurantProfileDTO {
    @JsonProperty("serverId")
    private Long id;

    @JsonProperty("localId")
    private Long localId;

    private String deviceId;
    private Long restaurantId;
    private Long updatedAt;
    private Boolean isDeleted;
    private Long serverUpdatedAt;
    private Long createdAt;

    private String shopName;
    private String shopAddress;
    private String whatsappNumber;
    private String email;
    private String fssaiNumber;
    private String fssaiExpiryDate;
    private String gstExpiryDate;
    private String logoPath;
    private String logoUrl;
    private Integer logoVersion;
    private String customWelcomeMessage;
    private String customFssaiMessage;
    private Boolean emailInvoiceConsent;
    private String country;
    private String currency;
    private String timezone;
    private Boolean gstEnabled;
    private String gstin;
    private Boolean isTaxInclusive;
    private BigDecimal gstPercentage;
    private String customTaxName;
    private String customTaxNumber;
    private BigDecimal customTaxPercentage;
    private Boolean upiEnabled;
    private String upiQrPath;
    private String upiQrUrl;
    private Integer upiQrVersion;
    private String upiHandle;
    private String upiMobile;
    private Boolean cashEnabled;
    private Boolean posEnabled;
    // Printer bindings (enabled/name/MAC/paper size, both roles) are intentionally absent:
    // printer config is device-local only. See RestaurantProfile.
    private Boolean autoPrintOnSuccess;
    private Boolean includeLogoInPrint;
    private Long dailyOrderCounter;
    private Long lifetimeOrderCounter;
    private String lastResetDate;
    private Integer sessionTimeoutMinutes;
    private String orderPaymentFlowMode = "pay_before_food";
    private String reviewUrl;
    private String invoiceFooter;
    private Boolean easebuzzEnabled;
    private Boolean showBranding;
    private Boolean maskCustomerPhone;
    private Boolean printCustomerWhatsapp;
    private String lastResetDateProper;

    /** Comma-separated list of profile fields the device actually changed (field-level merge). */
    private String changedFields;
}
