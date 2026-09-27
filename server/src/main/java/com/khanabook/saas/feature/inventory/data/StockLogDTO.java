package com.khanabook.saas.feature.inventory.data;

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

    /**
     * Must stay named "delta" to match the entity property and the client wire field.
     * BeanUtils.copyProperties matches by name, so a different name here binds nothing and
     * leaves the NOT NULL delta column unset, failing every stock-log push.
     */
    private java.math.BigDecimal delta;
    private String reason;
}
