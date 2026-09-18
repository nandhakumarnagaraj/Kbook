package com.khanabook.saas.feature.platform.dto;

@lombok.Builder
public record AdminBusinessListItemResponse(
        Long restaurantId,
        String shopName,
        String ownerName,
        String ownerLoginId,
        String whatsappNumber,
        String email,
        boolean websiteEnabled,
        boolean isSuspended,
        long staffCount,
        long menuCount,
        long orderCount,
        Long updatedAt
) {
}
