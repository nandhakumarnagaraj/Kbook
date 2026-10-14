package com.khanabook.saas.feature.payments.data;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminSettlementResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long restaurantId,
        String shopName,
        BigDecimal totalSettled,
        BigDecimal totalCommission,
        Long orderCount,
        Long lastSettledAt
) {
}
