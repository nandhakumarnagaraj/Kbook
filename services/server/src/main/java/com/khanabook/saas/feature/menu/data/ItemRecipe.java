package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.inventory.data.RawMaterial;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Getter
@Setter
public class ItemRecipe {

    private Long id;
    private Long restaurantId;
    private Long menuItemId;
    private RawMaterial rawMaterial;
    private BigDecimal quantityPerItem;
    private Boolean isDeleted = false;
    private Long createdAt;
    private Long updatedAt;
}
