package com.khanabook.saas.feature.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBillListItemResponse {
    private Long id;
    private Long restaurantId;
    private String shopName;
    private String invoiceNumber;
    private String dailyOrderDisplay;
    private BigDecimal totalAmount;
    private String paymentMode;
    private String paymentStatus;
    private String orderStatus;
    private String customerName;
    private Long createdAt;
}
