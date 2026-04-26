package com.khanabook.saas.storefront.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class CustomerOrderStatusResponse {
    String publicOrderCode;
    Long restaurantId;
    String customerName;
    String fulfillmentType;
    String orderStatus;
    String paymentStatus;
    String paymentMethod;
    String currency;
    BigDecimal subtotal;
    BigDecimal totalAmount;
    Long createdAt;
    List<LineItem> items;

    @Value
    @Builder
    public static class LineItem {
        String itemName;
        String variantName;
        Integer quantity;
        BigDecimal unitPrice;
        BigDecimal lineTotal;
        String specialInstruction;
    }
}
