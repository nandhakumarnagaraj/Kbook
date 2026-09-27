package com.khanabook.saas.feature.menu.data;

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
    /**
     * The app's on-hand quantity. Named "stock" on the wire, and the entity column is
     * current_stock, so BeanUtils cannot map this. current_stock stays server-owned and is
     * recomputed by ItemVariantRepository.recalculateStock from the stock-log ledger.
     */
    private java.math.BigDecimal stock;
    private Boolean trackStock;
    private Boolean isAvailable;
    /** Client-owned: sent by the app, and absent here sort_order was dropped on every push. */
    private Integer sortOrder;
}
