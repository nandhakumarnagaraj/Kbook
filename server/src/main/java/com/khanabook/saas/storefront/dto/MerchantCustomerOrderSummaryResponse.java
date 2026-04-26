package com.khanabook.saas.storefront.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class MerchantCustomerOrderSummaryResponse {
    Long orderId;
    String publicOrderCode;
    String customerName;
    String customerPhone;
    String fulfillmentType;
    String orderStatus;
    String paymentStatus;
    String paymentMethod;
    String sourceChannel;
    String currency;
    BigDecimal totalAmount;
    Long createdAt;
    Long updatedAt;
}
