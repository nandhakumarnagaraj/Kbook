package com.khanabook.saas.storefront.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class StorefrontCatalogResponse {
    Long restaurantId;
    String shopName;
    String currency;
    String whatsappNumber;
    String reviewUrl;
    List<CategoryView> categories;

    @Value
    @Builder
    public static class CategoryView {
        Long categoryId;
        String name;
        Boolean isVeg;
        List<MenuItemView> items;
    }

    @Value
    @Builder
    public static class MenuItemView {
        Long menuItemId;
        String name;
        String description;
        String foodType;
        BigDecimal basePrice;
        Boolean available;
        String stockStatus;
        List<VariantView> variants;
    }

    @Value
    @Builder
    public static class VariantView {
        Long variantId;
        String name;
        BigDecimal price;
        Boolean available;
        String stockStatus;
    }
}
