package com.khanabook.saas.webadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Temp token is required")
        String tempToken,

        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 128, message = "Password must be at least 6 characters")
        String newPassword
) {}
