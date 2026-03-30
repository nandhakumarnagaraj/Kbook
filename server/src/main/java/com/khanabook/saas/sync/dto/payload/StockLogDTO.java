package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

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

    private Long menuItemId;
    private Long serverMenuItemId;
    private MenuItem menuItem;
    private Long variantId;
    private Long serverVariantId;
    private ItemVariant itemVariant;
    private java.math.BigDecimal delta;
    private String reason;
}
