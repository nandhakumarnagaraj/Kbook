package com.khanabook.saas.webadmin.dto;

import java.math.BigDecimal;

public record RefundBillRequest(
        BigDecimal refundAmount,
        String reason
) {}
