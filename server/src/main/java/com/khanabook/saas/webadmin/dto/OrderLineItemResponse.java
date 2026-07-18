package com.khanabook.saas.webadmin.dto;

import java.math.BigDecimal;

public record OrderLineItemResponse(
        Long id,
        String itemName,
        String variantName,
        Integer quantity,
        BigDecimal price,
        BigDecimal itemTotal
) {
}
