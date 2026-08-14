package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMenuItemRequest(
        String name,

        Long categoryId,

        String foodType,

        String basePrice,

        String description
) {}
