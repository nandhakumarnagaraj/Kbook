package com.khanabook.saas.webadmin.dto;

@lombok.Builder
public record AdminBusinessListItemResponse(
        Long restaurantId,
        String shopName,
        String ownerName,
        String ownerLoginId,
        String whatsappNumber,
        String email,
        boolean websiteEnabled,
        boolean easebuzzEnabled,
        long staffCount,
        long menuCount,
        long orderCount,
        Long updatedAt
) {
}
