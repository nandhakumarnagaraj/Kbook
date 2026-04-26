package com.khanabook.saas.storefront.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class CreateCustomerOrderResponse {
    Long orderId;
    String publicOrderCode;
    String trackingToken;
    String orderStatus;
    String paymentStatus;
    String currency;
    BigDecimal subtotal;
    BigDecimal totalAmount;
}
