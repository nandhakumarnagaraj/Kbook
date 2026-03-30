package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.khanabook.saas.entity.*;
import java.math.BigDecimal;

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

    private Long categoryId;
    private Long serverCategoryId;
    private Category category;
    private String name;
    private java.math.BigDecimal basePrice;
    private String foodType;
    private String description;
    private String barcode;
    private Boolean isAvailable;
    private java.math.BigDecimal currentStock;
    private java.math.BigDecimal lowStockThreshold;
}
