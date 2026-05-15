package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportsController {

    private final BillRepository billRepository;
    private final RestaurantProfileRepository profileRepository;

    @GetMapping("/commission")
    public ResponseEntity<List<Map<String, Object>>> commissionReport() {
        List<Object[]> rows = billRepository.findCommissionSummary();

        Map<Long, String> shopNames = profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(RestaurantProfile::getRestaurantId, RestaurantProfile::getShopName,
                        (a, b) -> a != null ? a : b));

        List<Map<String, Object>> result = rows.stream().map(r -> {
            Long restaurantId = ((Number) r[0]).longValue();
            long orderCount = ((Number) r[1]).longValue();
            BigDecimal totalCommission = r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO;
            BigDecimal totalRevenue = r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO;
            BigDecimal avgRate = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? totalCommission.multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("restaurantId", restaurantId);
            map.put("shopName", shopNames.get(restaurantId));
            map.put("totalCommission", totalCommission);
            map.put("totalRevenue", totalRevenue);
            map.put("orderCount", orderCount);
            map.put("avgCommissionRate", avgRate);
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/payment-dashboard")
    public ResponseEntity<Map<String, Object>> paymentDashboard() {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(istZone);
        ZonedDateTime todayStartIst = nowIst.toLocalDate().atStartOfDay(istZone);
        long todayStartEpochMs = todayStartIst.toInstant().toEpochMilli();

        long totalEasebuzz = billRepository.countByMode("easebuzz");
        long totalCash = billRepository.countByMode("cash");
        long totalUpi = billRepository.countByMode("upi");
        long totalTransactions = totalEasebuzz + totalCash + totalUpi;

        long successEasebuzz = billRepository.countSuccessfulByMode("easebuzz");
        long successCash = billRepository.countSuccessfulByMode("cash");
        long successUpi = billRepository.countSuccessfulByMode("upi");
        long successfulTotal = successEasebuzz + successCash + successUpi;

        BigDecimal successRate = totalTransactions > 0
                ? BigDecimal.valueOf(successfulTotal * 100.0 / totalTransactions)
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Long> byMode = new LinkedHashMap<>();
        byMode.put("easebuzz", totalEasebuzz);
        byMode.put("cash", totalCash);
        byMode.put("upi", totalUpi);

        long todayCount = billRepository.countSince(todayStartEpochMs);
        BigDecimal todayRevenue = billRepository.sumRevenueSince(todayStartEpochMs);

        BigDecimal easebuzzSuccessRate = totalEasebuzz > 0
                ? BigDecimal.valueOf(successEasebuzz * 100.0 / totalEasebuzz)
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTransactions", totalTransactions);
        result.put("successRate", successRate);
        result.put("byMode", byMode);
        result.put("todayCount", todayCount);
        result.put("todayRevenue", todayRevenue);
        result.put("easebuzzSuccessRate", easebuzzSuccessRate);

        return ResponseEntity.ok(result);
    }
}
