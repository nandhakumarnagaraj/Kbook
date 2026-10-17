package com.khanabook.saas.feature.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Owner edit of a bill's order status.
 *
 * <p>{@code reason} is mandatory: a status change moves recognised revenue and the
 * daily closing figures, so every edit must be explainable after the fact
 * (it is written to the security audit log).
 */
public record UpdateBillStatusRequest(
        @NotBlank String orderStatus,
        @NotBlank @Size(min = 5, max = 500) String reason
) {}
