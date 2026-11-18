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
public class StockMovement {

    public static final String KIND_PURCHASE = "PURCHASE";
    public static final String KIND_WASTAGE = "WASTAGE";
    public static final String KIND_SALES_DEDUCT = "SALES_DEDUCT";
    public static final String KIND_ADJUST = "ADJUST";
    public static final String KIND_OPENING = "OPENING";

    private Long id;
    private Long restaurantId;
    private RawMaterial rawMaterial;
    private String kind;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private Long vendorId;
    private String note;
    private String reason;
    private Long createdByUserId;
    private Long billId;
    private Long poId;
    private Long createdAt;
}
