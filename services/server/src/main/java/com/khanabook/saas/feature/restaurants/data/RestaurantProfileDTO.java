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
    private String logoUrl;
    private Integer logoVersion;
    private String currency;
    private String timezone;

    /**
     * Trading hours, local shop time.
     */
    private java.time.LocalTime openingTime;
    private java.time.LocalTime closingTime;
    private Boolean gstEnabled;
    private String gstin;
    private Boolean isTaxInclusive;
    private BigDecimal gstPercentage;
    private Boolean upiEnabled;
    private String upiHandle;
    private Boolean cashEnabled;
    private Boolean posEnabled;
    private Long dailyOrderCounter;
    private Long lifetimeOrderCounter;
    private String lastResetDate;
    private Integer sessionTimeoutMinutes;
    private String orderPaymentFlowMode = "pay_before_food";
    private String reviewUrl;
    private String invoiceFooter;
    private String lastResetDateProper;

    /** Comma-separated list of profile fields the device actually changed (field-level merge). */
    private String changedFields;
}
