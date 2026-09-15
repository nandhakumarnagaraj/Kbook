package com.khanabook.saas.sync.dto.payload;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class BillItemDTO {
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

    private Long billId;
    @JsonProperty("billLocalId")
    private Long billLocalId;
    private Long serverBillId;

    private String itemName;
    private String variantName;
    private Long menuItemId;
    private Long serverMenuItemId;
    private Long variantId;
    private Long serverVariantId;
    private Integer quantity;
    private java.math.BigDecimal price;
    @JsonProperty("itemTotal")
    private java.math.BigDecimal itemTotal;
}
