package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class MenuItemDTO {
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

    private String name;
    private Long categoryId;
    @JsonProperty("categoryLocalId")
    private Long categoryLocalId;
    private Long serverCategoryId;

    private java.math.BigDecimal basePrice;
    private String imageUrl;
    private Boolean isVeg;
    private Boolean isAvailable;
    private String description;
    private Integer sortOrder;
    private Boolean overwriteExisting;
}
