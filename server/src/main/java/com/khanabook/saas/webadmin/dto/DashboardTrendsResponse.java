package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record DashboardTrendsResponse(
        List<DayTrend> last7Days,
        long todayRevenue,
        long yesterdayRevenue,
        long thisWeekRevenue,
        long lastWeekRevenue
) {
    @Builder
    public record DayTrend(
            String day,
            long revenue,
            long orderCount,
            long avgOrderValue
    ) {
    }
}
