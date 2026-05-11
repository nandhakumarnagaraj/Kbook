package com.khanabook.saas.integration.dto;

import lombok.Builder;

@Builder
public record MarketplaceConfigResponse(
        boolean zomatoEnabled,
        String zomatoApiKeyMasked,
        String zomatoOutletId,
        String zomatoWebhookUrl,
        boolean swiggyEnabled,
        String swiggyApiKeyMasked,
        String swiggyStoreId,
        String swiggyWebhookUrl
) {
}
