package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminSettlementResponse(
        Long restaurantId,
        String shopName,
        BigDecimal totalSettled,
        BigDecimal totalCommission,
        Long orderCount,
        Long lastSettledAt
) {
}
