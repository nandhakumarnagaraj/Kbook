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
public class PurchaseOrderItem {

    private Long id;
    private PurchaseOrder purchaseOrder;
    private RawMaterial rawMaterial;
    private BigDecimal quantity;
}
