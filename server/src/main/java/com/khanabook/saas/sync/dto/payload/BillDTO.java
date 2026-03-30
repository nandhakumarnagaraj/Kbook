package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

@Data
public class BillDTO {
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

    private Long dailyOrderId;
    private String dailyOrderDisplay;
    private Long lifetimeOrderId;
    private String orderType;
    private String customerName;
    private String customerWhatsapp;
    private java.math.BigDecimal subtotal;
    private java.math.BigDecimal gstPercentage;
    private java.math.BigDecimal cgstAmount;
    private java.math.BigDecimal sgstAmount;
    private java.math.BigDecimal customTaxAmount;
    private java.math.BigDecimal totalAmount;
    private String paymentMode;
    private java.math.BigDecimal partAmount1;
    private java.math.BigDecimal partAmount2;
    private String paymentStatus;
    private String orderStatus;
    private Long createdBy;
    private Long paidAt;
    private String lastResetDate;
}
