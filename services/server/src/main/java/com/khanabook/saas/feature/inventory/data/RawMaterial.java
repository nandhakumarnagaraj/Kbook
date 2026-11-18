package com.khanabook.saas.feature.inventory.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Getter
@Setter
public class RawMaterial {

    private Long id;
    private Long version = 0L;
    private Long restaurantId;
    private String name;
    private String unit = "kg";
    private BigDecimal stockQuantity = BigDecimal.ZERO;
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;
    private BigDecimal costPerUnit;
    private Boolean isDeleted = false;
    private Long createdAt;
    private Long updatedAt;
    private Long expiryDate;
}
