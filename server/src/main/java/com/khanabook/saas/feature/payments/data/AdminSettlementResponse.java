package com.khanabook.saas.feature.payments.data;

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
