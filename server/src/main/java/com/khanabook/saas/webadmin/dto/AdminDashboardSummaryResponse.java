package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminDashboardSummaryResponse(
        long totalBusinesses,
        long liveBusinesses,
        long totalStaff,
        long totalOrders,
        BigDecimal totalRevenue
) {
}
