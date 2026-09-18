package com.khanabook.saas.feature.reports.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminDashboardSummaryResponse(
        long totalBusinesses,
        long liveBusinesses,
        long totalStaff,
        long totalOrders,
        BigDecimal totalRevenue,
        long refundedOrders,
        BigDecimal refundedAmount
) {
}
