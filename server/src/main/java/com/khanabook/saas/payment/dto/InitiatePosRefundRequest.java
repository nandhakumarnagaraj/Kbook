package com.khanabook.saas.payment.dto;

import java.math.BigDecimal;

public record InitiatePosRefundRequest(BigDecimal refundAmount, String reason) {}
