package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Append-only stock movement ledger. Every quantity change to a raw material
 * is recorded here: PURCHASE (+), WASTAGE (-), SALES_DEDUCT (-),
 * ADJUST (+/-, physical count reconciliation), OPENING (initial).
 */
@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_stock_movements_lookup", columnList = "restaurant_id, raw_material_id, created_at")
})
@Getter
@Setter
public class StockMovement {

    public static final String KIND_PURCHASE = "PURCHASE";
    public static final String KIND_WASTAGE = "WASTAGE";
    public static final String KIND_SALES_DEDUCT = "SALES_DEDUCT";
    public static final String KIND_ADJUST = "ADJUST";
    public static final String KIND_OPENING = "OPENING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(nullable = false, length = 20)
    private String kind;

    /** Positive = stock in, negative = stock out. */
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
