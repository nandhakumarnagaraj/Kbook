package com.khanabook.saas.feature.business.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record BusinessMenuListItemResponse(
        Long menuItemId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String foodType,
        BigDecimal basePrice,
        boolean available,
        /** The owner's pin for this dish (V109). Delivered so both surfaces can show the star. */
        boolean favourite,
        String stockStatus,
        long variantCount,
        List<BusinessItemVariantResponse> variants,
        String imageUrl,
        Integer imageVersion,
        Long updatedAt
) {
}
