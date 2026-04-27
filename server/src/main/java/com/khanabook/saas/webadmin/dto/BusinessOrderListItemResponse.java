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
        BigDecimal refundAmount,
        String refundStatus,
        String refundMode,
        String cancelReason,
        boolean manualRefundAllowed,
        boolean gatewayRefundAllowed,
        Long createdAt
) {
}
