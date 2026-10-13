package com.khanabook.saas.feature.inventory.data;

public class PurchaseOrderDtos {

    public record PoLine(Long rawMaterialId, java.math.BigDecimal quantity) {}
    public record PoItem(Long rawMaterialId, String materialName, String unit, java.math.BigDecimal quantity) {}
    public record PoResponse(Long id, Long vendorId, String status, String note, Long createdAt, java.util.List<PoItem> items) {}
}
