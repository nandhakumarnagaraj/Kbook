package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
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
    private String logoPath;
    private String fssaiNumber;
    private Boolean emailInvoiceConsent;
    private String country;
    private Boolean gstEnabled;
    private String gstin;
    private Boolean isTaxInclusive;
    private java.math.BigDecimal gstPercentage;
    private String customTaxName;
    private String customTaxNumber;
    private java.math.BigDecimal customTaxPercentage;
    private String currency;
    private Boolean upiEnabled;
    private String upiQrPath;
    private String upiHandle;
    private String upiMobile;
    private Boolean cashEnabled;
    private Boolean posEnabled;
    private Boolean zomatoEnabled;
    private Boolean swiggyEnabled;
    private Boolean ownWebsiteEnabled;
    private Boolean printerEnabled;
    private String printerName;
    private String printerMac;
    private String paperSize;
    private Boolean kitchenPrinterEnabled;
    private String kitchenPrinterName;
    private String kitchenPrinterMac;
    private String kitchenPrinterPaperSize;
    private Boolean autoPrintOnSuccess;
    private Boolean includeLogoInPrint;
    private Boolean printCustomerWhatsapp;
    private Long dailyOrderCounter;
    private Long lifetimeOrderCounter;
    private String lastResetDate;
    private Integer sessionTimeoutMinutes;
    private String timezone;
    private String reviewUrl;
    private Boolean showBranding;
    private Boolean maskCustomerPhone;
}
