package com.khanabook.saas.webadmin.dto;

import com.khanabook.saas.billing.entity.SubMerchantStatus;
import lombok.Builder;

@Builder
public record BusinessMarketplaceSetupResponse(
        Long restaurantId,
        String shopName,
        Boolean paymentManagedByAdmin,
        SubMerchantStatus subMerchantStatus,
        String subMerchantId,
        String kycPortalUrl,
        Long kycSubmittedAt,
        Long kycActivatedAt
) {
}
