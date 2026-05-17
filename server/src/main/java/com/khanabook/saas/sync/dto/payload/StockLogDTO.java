package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class StockLogDTO {
    @JsonProperty("serverId")
    private Long id;

    @JsonProperty("localId")
    private Long localId;

    private String deviceId;
    private Long restaurantId;
    private Long updatedAt;
    private Boolean isDeleted;
    private Long serverUpdatedAt;
    private Long createdAt;

    private Long variantId;
    @JsonProperty("variantLocalId")
    private Long variantLocalId;
    private Long menuItemId;
    private Long serverMenuItemId;
    private Long serverVariantId;

    private java.math.BigDecimal changeAmount;
    private String reason;
    private String referenceId;
}
