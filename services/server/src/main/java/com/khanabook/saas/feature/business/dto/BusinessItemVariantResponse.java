package com.khanabook.saas.feature.business.dto;

import java.math.BigDecimal;

public record BusinessItemVariantResponse(
        Long variantId,
        Long menuItemId,
        String variantName,
        BigDecimal price,
        Boolean isAvailable,
        Integer sortOrder
) {}
