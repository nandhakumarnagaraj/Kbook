package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record BusinessDashboardResponse(
        Long restaurantId,
        String shopName,
        long totalStaff,
        long totalMenuItems,
        long posOrderCount,
        long onlineOrderCount,
        long pendingOnlineOrders,
        BigDecimal totalRevenue,
        BigDecimal todayRevenue,
        List<BusinessOrderListItemResponse> recentOrders
) {
}
