package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BusinessOrderListItemResponse(
        String sourceType,
        Long orderId,
        String orderCode,
        String customerName,
        String customerContact,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        BigDecimal totalAmount,
        Long createdAt
) {
}
