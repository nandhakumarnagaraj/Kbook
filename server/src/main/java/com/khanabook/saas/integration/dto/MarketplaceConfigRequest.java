package com.khanabook.saas.integration.dto;

public record MarketplaceConfigRequest(
        String zomatoApiKey,
        String zomatoWebhookSecret,
        Boolean zomatoEnabled,
        String swiggyApiKey,
        String swiggyWebhookSecret,
        Boolean swiggyEnabled
) {
}
