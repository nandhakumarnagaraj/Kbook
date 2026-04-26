package com.khanabook.saas.storefront.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_items", indexes = {
        @Index(name = "idx_customer_order_items_order", columnList = "customer_order_id")
})
@Getter
@Setter
public class CustomerOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_order_id", nullable = false)
    private Long customerOrderId;

    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Column(name = "item_variant_id")
    private Long itemVariantId;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "variant_name", length = 255)
    private String variantName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal lineTotal;

    @Column(name = "special_instruction", columnDefinition = "TEXT")
    private String specialInstruction;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
