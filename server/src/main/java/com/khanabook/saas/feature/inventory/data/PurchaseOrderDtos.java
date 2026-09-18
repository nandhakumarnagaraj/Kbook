package com.khanabook.saas.feature.inventory.data;

public class PurchaseOrderDtos {

    public record PoLine(Long rawMaterialId, java.math.BigDecimal quantity) {}
}