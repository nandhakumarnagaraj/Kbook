package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "marketplace_order_items", indexes = {
    @Index(name = "idx_marketplace_order_items_order", columnList = "marketplace_order_id"),
    @Index(name = "idx_marketplace_order_items_bill_item", columnList = "bill_item_id")
})
@Getter
@Setter
public class MarketplaceOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "marketplace_order_id", nullable = false)
    private Long marketplaceOrderId;

    @Column(name = "bill_item_id")
    private Long billItemId;

    @Column(name = "platform_item_id")
    private String platformItemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "price", columnDefinition = "NUMERIC(12,2)", nullable = false)
    private java.math.BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "item_total", columnDefinition = "NUMERIC(12,2)", nullable = false)
    private java.math.BigDecimal itemTotal;

    @Column(name = "special_instruction", columnDefinition = "TEXT")
    private String specialInstruction;
}
