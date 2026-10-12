package com.khanabook.saas.feature.business.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BusinessOrderListItemResponse(
        String sourceType,
        String orderType,
        Long orderId,
        String orderCode,
        String invoiceNumber,
        String publicToken,
        String customerName,
        String customerContact,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal gatewayPaidAmount,
        BigDecimal refundAmount,
        String refundStatus,
        String refundMode,
        String cancelReason,
        boolean manualRefundAllowed,
        boolean gatewayRefundAllowed,
        Long createdAt
) {
}
