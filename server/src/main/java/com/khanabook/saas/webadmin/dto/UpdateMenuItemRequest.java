package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMenuItemRequest(
        String name,

        Long categoryId,

        String foodType,

        String basePrice,

        String description,

        String imageUrl
) {
    public UpdateMenuItemRequest(String name, Long categoryId, String foodType, String basePrice, String description) {
        this(name, categoryId, foodType, basePrice, description, null);
    }
}
