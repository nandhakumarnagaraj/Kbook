package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

@Builder
public record BusinessMarketplaceSetupResponse(
        Long restaurantId,
        String shopName,
        Boolean paymentManagedByAdmin,
        String subMerchantStatus,
        String subMerchantId,
        String kycPortalUrl,
        Long kycSubmittedAt,
        Long kycActivatedAt
) {
}
