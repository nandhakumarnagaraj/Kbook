package com.khanabook.saas.webadmin.dto;

public record MarketplaceConfigResponse(
        Boolean zomatoEnabled,
        String zomatoApiKeyMasked,
        String zomatoOutletId,
        String zomatoWebhookUrl,
        Boolean swiggyEnabled,
        String swiggyApiKeyMasked,
        String swiggyStoreId,
        String swiggyWebhookUrl
) {
}
