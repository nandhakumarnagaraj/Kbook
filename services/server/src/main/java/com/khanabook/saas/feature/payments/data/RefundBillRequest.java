package com.khanabook.saas.feature.payments.data;

import java.math.BigDecimal;

public record RefundBillRequest(
        BigDecimal refundAmount,
        String reason
) {}
