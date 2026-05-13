package com.khanabook.saas.webadmin.dto;

public record UpdateBusinessIntegrationsRequest(
        Boolean zomatoEnabled,
        String zomatoOutletId,
        Boolean swiggyEnabled,
        String swiggyStoreId,
        String marketplaceNotes
) {
}
