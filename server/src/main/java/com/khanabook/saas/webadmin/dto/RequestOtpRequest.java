package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestOtpRequest(
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
        String phone
) {}
