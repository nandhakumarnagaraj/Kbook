package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
        String phone,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{4,6}$", message = "OTP must be 4-6 digits")
        String otp
) {}
