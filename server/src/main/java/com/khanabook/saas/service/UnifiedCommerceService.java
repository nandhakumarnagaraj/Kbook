package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
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
    private final MarketplaceOrderRepository marketplaceOrderRepository;

    public Map<String, Object> getUnifiedDashboard(Long restaurantId) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(ist);
        long todayStartMs = today.atStartOfDay(ist).toInstant().toEpochMilli();

        List<Bill> posBills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        List<MarketplaceOrder> marketplaceOrders = marketplaceOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        long posToday = posBills.stream().filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= todayStartMs).count();
        long swiggyToday = marketplaceOrders.stream().filter(o -> "SWIGGY".equals(o.getPlatform()) && o.getCreatedAt() != null && o.getCreatedAt() >= todayStartMs).count();
        long zomatoToday = marketplaceOrders.stream().filter(o -> "ZOMATO".equals(o.getPlatform()) && o.getCreatedAt() != null && o.getCreatedAt() >= todayStartMs).count();
        long totalToday = posToday + swiggyToday + zomatoToday;

        BigDecimal posRevenueToday = posBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= todayStartMs)
                .map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mpRevenueToday = marketplaceOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt() >= todayStartMs)
                .map(MarketplaceOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalPosAllTime = posBills.size();
        long totalMpAllTime = marketplaceOrders.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("today", Map.of("pos", posToday, "swiggy", swiggyToday, "zomato", zomatoToday, "total", totalToday));
        result.put("todayRevenue", Map.of("pos", posRevenueToday, "marketplace", mpRevenueToday, "total", posRevenueToday.add(mpRevenueToday)));
        result.put("allTime", Map.of("pos", totalPosAllTime, "marketplace", totalMpAllTime, "total", totalPosAllTime + totalMpAllTime));
        result.put("channelBreakdown", List.of(
            Map.of("channel", "POS", "todayOrders", posToday, "totalOrders", totalPosAllTime),
            Map.of("channel", "Swiggy", "todayOrders", swiggyToday, "totalOrders", marketplaceOrders.stream().filter(o -> "SWIGGY".equals(o.getPlatform())).count()),
            Map.of("channel", "Zomato", "todayOrders", zomatoToday, "totalOrders", marketplaceOrders.stream().filter(o -> "ZOMATO".equals(o.getPlatform())).count())
        ));
        return result;
    }

    public Map<String, Object> getCrossChannelInsights(Long restaurantId) {
        List<Bill> posBills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        List<MarketplaceOrder> mpOrders = marketplaceOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        BigDecimal avgPosOrder = posBills.isEmpty() ? BigDecimal.ZERO :
            posBills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(posBills.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgMpOrder = mpOrders.isEmpty() ? BigDecimal.ZERO :
            mpOrders.stream().map(MarketplaceOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(mpOrders.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgOrderValue", Map.of("pos", avgPosOrder, "marketplace", avgMpOrder));
        result.put("totalOrders", Map.of("pos", posBills.size(), "marketplace", mpOrders.size()));
        return result;
    }
}