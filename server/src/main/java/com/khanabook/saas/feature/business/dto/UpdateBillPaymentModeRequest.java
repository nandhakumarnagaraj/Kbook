package com.khanabook.saas.feature.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Owner edit of a bill's payment mode.
 *
 * <p>{@code partAmount1}/{@code partAmount2} are only written when the target mode is a
 * part-payment mode; see {@code BillEditService} for what happens to the split amounts
 * when a bill is moved off a part-payment mode.
 */
public record UpdateBillPaymentModeRequest(
        @NotBlank String paymentMode,
        BigDecimal partAmount1,
        BigDecimal partAmount2,
        @NotBlank @Size(min = 5, max = 500) String reason
) {}
