package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ItemVariantDTO {
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

    private Long itemId;
    @JsonProperty("itemLocalId")
    private Long itemLocalId;
    private Long menuItemId;
    private Long serverMenuItemId;

    private String variantName;
    private java.math.BigDecimal price;
    private java.math.BigDecimal stock;
    private Boolean trackStock;
    private Boolean isAvailable;
}
