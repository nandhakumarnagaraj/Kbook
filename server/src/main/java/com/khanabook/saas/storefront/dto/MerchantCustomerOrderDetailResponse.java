package com.khanabook.saas.storefront.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class MerchantCustomerOrderDetailResponse {
    Long orderId;
    Long restaurantId;
    String publicOrderCode;
    String trackingToken;
    String customerName;
    String customerPhone;
    String customerNote;
    String fulfillmentType;
    String orderStatus;
    String paymentStatus;
    String paymentMethod;
    String sourceChannel;
    String currency;
    BigDecimal subtotal;
    BigDecimal totalAmount;
    Long createdAt;
    Long updatedAt;
    List<LineItem> items;

    @Value
    @Builder
    public static class LineItem {
        Long menuItemId;
        Long itemVariantId;
        String itemName;
        String variantName;
        Integer quantity;
        BigDecimal unitPrice;
        BigDecimal lineTotal;
        String specialInstruction;
    }
}
