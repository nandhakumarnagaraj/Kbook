package com.khanabook.saas.dto;

public class PurchaseOrderDtos {

    public record PoLine(Long rawMaterialId, java.math.BigDecimal quantity) {}
}