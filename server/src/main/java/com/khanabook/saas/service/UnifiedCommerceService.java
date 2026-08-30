package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnifiedCommerceService {

    private final BillRepository billRepository;

    public Map<String, Object> getUnifiedDashboard(Long restaurantId) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(ist);
        long todayStartMs = today.atStartOfDay(ist).toInstant().toEpochMilli();

        List<Bill> posBills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);

        long posToday = posBills.stream().filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= todayStartMs).count();
        long totalToday = posToday;

        BigDecimal posRevenueToday = posBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= todayStartMs)
                .map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalPosAllTime = posBills.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("today", Map.of("pos", posToday, "total", totalToday));
        result.put("todayRevenue", Map.of("pos", posRevenueToday, "total", posRevenueToday));
        result.put("allTime", Map.of("pos", totalPosAllTime, "total", totalPosAllTime));
        result.put("channelBreakdown", List.of(
            Map.of("channel", "POS", "todayOrders", posToday, "totalOrders", totalPosAllTime)
        ));
        return result;
    }

    public Map<String, Object> getCrossChannelInsights(Long restaurantId) {
        List<Bill> posBills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);

        BigDecimal avgPosOrder = posBills.isEmpty() ? BigDecimal.ZERO :
            posBills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(posBills.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgOrderValue", Map.of("pos", avgPosOrder));
        result.put("totalOrders", Map.of("pos", posBills.size()));
        return result;
    }
}
