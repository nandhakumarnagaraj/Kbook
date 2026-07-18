package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMenuItemRequest(
        @NotBlank(message = "Name is required")
        String name,

        Long categoryId,

        String foodType,

        @NotBlank(message = "Base price is required")
        String basePrice,

        String description
) {}
