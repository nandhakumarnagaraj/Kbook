package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "item_recipes", indexes = {
    @Index(name = "idx_item_recipes_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_item_recipes_menu_item", columnList = "restaurant_id, menu_item_id"),
    @Index(name = "idx_item_recipes_material", columnList = "raw_material_id")
})
@Getter
@Setter
public class ItemRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(name = "quantity_per_item", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantityPerItem;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
