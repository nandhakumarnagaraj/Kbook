package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record UpdateStaffRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
        String phone,

        String email,

        @NotNull(message = "Role is required")
        String role
) {}
