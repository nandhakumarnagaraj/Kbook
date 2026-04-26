package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminBusinessDetailResponse(
        Long restaurantId,
        String shopName,
        String ownerName,
        String ownerLoginId,
        String ownerWhatsappNumber,
        String email,
        String shopAddress,
        String currency,
        String timezone,
        boolean websiteEnabled,
        boolean gstEnabled,
        boolean printerEnabled,
        long staffCount,
        long menuCount,
        long posOrderCount,
        long onlineOrderCount,
        BigDecimal totalRevenue,
        Long createdAt,
        Long updatedAt
) {
}
