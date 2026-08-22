package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "raw_materials", indexes = {
    @Index(name = "idx_raw_materials_restaurant", columnList = "restaurant_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_raw_materials_restaurant_name", columnNames = {"restaurant_id", "name"})
})
@Getter
@Setter
public class RawMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "kg";

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "low_stock_threshold", nullable = false, precision = 12, scale = 4)
    private BigDecimal lowStockThreshold = BigDecimal.ZERO;

    @Column(name = "cost_per_unit", precision = 12, scale = 2)
    private BigDecimal costPerUnit;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
