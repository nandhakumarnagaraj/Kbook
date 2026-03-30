package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

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

    private Long menuItemId;
    private Long serverMenuItemId;
    private MenuItem menuItem;
    private String variantName;
    private java.math.BigDecimal price;
    private Boolean isAvailable;
    private Integer sortOrder;
    private java.math.BigDecimal currentStock;
    private java.math.BigDecimal lowStockThreshold;
}
