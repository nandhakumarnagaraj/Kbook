package com.khanabook.saas.feature.platform.dto;

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
        String gstin,
        String fssaiNumber,
        String whatsappNumber,
        String currency,
        String timezone,
        boolean websiteEnabled,
        boolean gstEnabled,
        long staffCount,
        long menuCount,
        long posOrderCount,
        BigDecimal totalRevenue,
        Long createdAt,
        Long updatedAt
) {
}
