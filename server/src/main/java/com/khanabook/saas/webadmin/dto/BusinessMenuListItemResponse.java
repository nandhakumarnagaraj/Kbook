package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BusinessMenuListItemResponse(
        Long menuItemId,
        String categoryName,
        String name,
        String description,
        String foodType,
        BigDecimal basePrice,
        boolean available,
        String stockStatus,
        long variantCount,
        Long updatedAt
) {
}
