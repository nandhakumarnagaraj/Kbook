package com.khanabook.saas.feature.platform.dto;

import lombok.Builder;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

@Builder
public record AdminBusinessDetailResponse(
        // See AdminBusinessListItemResponse: keep large IDs exact in browsers.
        @JsonSerialize(using = ToStringSerializer.class)
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
