package com.khanabook.saas.feature.payments.data;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminTransactionResponse(
        Long id,
        Long restaurantId,
        String shopName,
        String txnId,
        String easebuzzId,
        String status,
        BigDecimal amount,
        Long receivedAt
) {
}
