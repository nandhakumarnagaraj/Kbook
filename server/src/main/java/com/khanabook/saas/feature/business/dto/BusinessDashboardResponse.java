package com.khanabook.saas.feature.business.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record BusinessDashboardResponse(
        Long restaurantId,
        String shopName,
        boolean websiteEnabled,
        long totalStaff,
        long totalMenuItems,
        long posOrderCount,
        long pendingPosPayments,
        BigDecimal totalRevenue,
        BigDecimal todayRevenue,
        long refundedOrders,
        BigDecimal refundedAmount,
        List<BusinessOrderListItemResponse> recentOrders
) {
}
