package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentMetricsService {

    private final EasebuzzWebhookEventRepository eventRepository;
    private final BillRepository billRepository;
    private final RestaurantProfileRepository profileRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public Map<String, Object> getOverview() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        ZonedDateTime todayStart = nowIst.toLocalDate().atStartOfDay(ist);
        ZonedDateTime lastHour = nowIst.minusHours(1);

        long todayStartMs = todayStart.toInstant().toEpochMilli();
        long lastHourMs = lastHour.toInstant().toEpochMilli();
        long nowMs = nowIst.toInstant().toEpochMilli();

        long totalEvents = eventRepository.count();
        long successEvents = eventRepository.countByReceivedAtBetweenAndStatus(0L, nowMs, "success");
        long failedEvents = eventRepository.countByReceivedAtBetweenAndStatus(0L, nowMs, "failure")
                          + eventRepository.countByReceivedAtBetweenAndStatus(0L, nowMs, "failed");
        long totalTxns = successEvents + failedEvents;
        BigDecimal overallSuccessRate = totalTxns > 0
                ? BigDecimal.valueOf(successEvents * 100.0 / totalTxns).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long todayTotal = eventRepository.countByReceivedAtBetween(todayStartMs, nowMs);
        long todaySuccess = eventRepository.countByReceivedAtBetweenAndStatus(todayStartMs, nowMs, "success");
        long todayFailed = eventRepository.countByReceivedAtBetweenAndStatus(todayStartMs, nowMs, "failure")
                         + eventRepository.countByReceivedAtBetweenAndStatus(todayStartMs, nowMs, "failed");
        BigDecimal todaySuccessRate = todayTotal > 0
                ? BigDecimal.valueOf(todaySuccess * 100.0 / todayTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long lastHourTotal = eventRepository.countByReceivedAtBetween(lastHourMs, nowMs);
        long lastHourSuccess = eventRepository.countByReceivedAtBetweenAndStatus(lastHourMs, nowMs, "success");
        BigDecimal lastHourSuccessRate = lastHourTotal > 0
                ? BigDecimal.valueOf(lastHourSuccess * 100.0 / lastHourTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal todayRevenue = billRepository.sumRevenueSince(todayStartMs);
        long todayOrders = billRepository.countSince(todayStartMs);

        List<Map<String, Object>> byMethod = getPaymentMethodBreakdown(todayStartMs, nowMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTransactions", totalEvents);
        result.put("successfulTransactions", successEvents);
        result.put("failedTransactions", failedEvents);
        result.put("overallSuccessRate", overallSuccessRate);
        result.put("todayTotal", todayTotal);
        result.put("todaySuccess", todaySuccess);
        result.put("todayFailed", todayFailed);
        result.put("todaySuccessRate", todaySuccessRate);
        result.put("lastHourTotal", lastHourTotal);
        result.put("lastHourSuccess", lastHourSuccess);
        result.put("lastHourSuccessRate", lastHourSuccessRate);
        result.put("todayRevenue", todayRevenue);
        result.put("todayOrders", todayOrders);
        result.put("byPaymentMethod", byMethod);
        return result;
    }

    public List<Map<String, Object>> getTrends(String period, int days) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        ZonedDateTime fromIst = nowIst.minusDays(days);
        long fromMs = fromIst.toInstant().toEpochMilli();
        long toMs = nowIst.toInstant().toEpochMilli();

        List<EasebuzzWebhookEvent> events = eventRepository.findByReceivedAtBetween(fromMs, toMs);

        Map<Long, List<EasebuzzWebhookEvent>> buckets;
        if ("hourly".equalsIgnoreCase(period)) {
            buckets = events.stream()
                    .collect(Collectors.groupingBy(e -> truncateHour(e.getReceivedAt())));
        } else if ("weekly".equalsIgnoreCase(period)) {
            buckets = events.stream()
                    .collect(Collectors.groupingBy(e -> truncateWeek(e.getReceivedAt())));
        } else {
            buckets = events.stream()
                    .collect(Collectors.groupingBy(e -> truncateDay(e.getReceivedAt())));
        }

        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EasebuzzWebhookEvent> bucketEvents = entry.getValue();
                    long total = bucketEvents.size();
                    long success = bucketEvents.stream().filter(e -> "success".equals(e.getStatus())).count();
                    long failed = bucketEvents.stream().filter(e -> "failure".equals(e.getStatus()) || "failed".equals(e.getStatus())).count();
                    BigDecimal rate = total > 0
                            ? BigDecimal.valueOf(success * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("timestamp", entry.getKey());
                    point.put("total", total);
                    point.put("successful", success);
                    point.put("failed", failed);
                    point.put("successRate", rate);
                    return point;
                })
                .toList();
    }

    public Map<String, Object> getFailedTransactions(int page, int size) {
        List<EasebuzzWebhookEvent> allFailed = eventRepository.findFailedTransactions();
        int total = allFailed.size();
        int from = page * size;
        int to = Math.min(from + size, total);

        Map<Long, String> shopNames = profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(RestaurantProfile::getRestaurantId, RestaurantProfile::getShopName,
                        (a, b) -> a != null ? a : b));

        List<Map<String, Object>> items = allFailed.subList(from, to).stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("restaurantId", e.getRestaurantId());
            item.put("shopName", shopNames.get(e.getRestaurantId()));
            item.put("txnId", e.getTxnId());
            item.put("easebuzzId", e.getEasebuzzId());
            item.put("amount", e.getAmount());
            item.put("status", e.getStatus());
            item.put("receivedAt", e.getReceivedAt());
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", items);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("number", page);
        result.put("size", size);
        return result;
    }

    public List<Map<String, Object>> getAnomalies() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        ZonedDateTime todayStart = nowIst.toLocalDate().atStartOfDay(ist);
        ZonedDateTime yesterdayStart = todayStart.minusDays(1);
        long yesterdayStartMs = yesterdayStart.toInstant().toEpochMilli();
        long todayStartMs = todayStart.toInstant().toEpochMilli();

        long yesterdayTotal = eventRepository.countByReceivedAtBetween(yesterdayStartMs, todayStartMs);
        long yesterdaySuccess = eventRepository.countByReceivedAtBetweenAndStatus(yesterdayStartMs, todayStartMs, "success");
        BigDecimal yesterdayRate = yesterdayTotal > 0
                ? BigDecimal.valueOf(yesterdaySuccess * 100.0 / yesterdayTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long todayTotal = eventRepository.countByReceivedAtBetween(todayStartMs, nowIst.toInstant().toEpochMilli());
        long todaySuccess = eventRepository.countByReceivedAtBetweenAndStatus(todayStartMs, nowIst.toInstant().toEpochMilli(), "success");
        BigDecimal todayRate = todayTotal > 0
                ? BigDecimal.valueOf(todaySuccess * 100.0 / todayTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal drop = yesterdayRate.compareTo(BigDecimal.ZERO) > 0
                ? yesterdayRate.subtract(todayRate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Map<String, Object>> anomalies = new ArrayList<>();
        if (drop.compareTo(BigDecimal.valueOf(3)) > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "success_rate_drop");
            alert.put("severity", drop.compareTo(BigDecimal.valueOf(5)) > 0 ? "critical" : "warning");
            alert.put("message", "Success rate dropped from " + yesterdayRate + "% to " + todayRate + "% (?" + drop + "%)");
            alert.put("yesterdayRate", yesterdayRate);
            alert.put("todayRate", todayRate);
            alert.put("drop", drop);
            anomalies.add(alert);
        }

        List<Map<String, Object>> failingBusinesses = getFailingBusinesses(todayStartMs);
        anomalies.addAll(failingBusinesses);

        return anomalies;
    }

    private List<Map<String, Object>> getFailingBusinesses(long sinceMs) {
        List<Object[]> counts = eventRepository.countByRestaurantAndStatusBetween(sinceMs, System.currentTimeMillis());
        Map<Long, Map<String, Long>> bizStatus = new HashMap<>();
        for (Object[] row : counts) {
            Long rid = ((Number) row[0]).longValue();
            String status = (String) row[1];
            long cnt = ((Number) row[2]).longValue();
            bizStatus.computeIfAbsent(rid, k -> new HashMap<>())
                    .merge(status, cnt, Long::sum);
        }

        Map<Long, String> shopNames = profileRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(RestaurantProfile::getRestaurantId, RestaurantProfile::getShopName,
                        (a, b) -> a != null ? a : b));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Long>> entry : bizStatus.entrySet()) {
            Map<String, Long> stats = entry.getValue();
            long total = stats.values().stream().mapToLong(v -> v).sum();
            long success = stats.getOrDefault("success", 0L);
            if (total >= 10) {
                BigDecimal rate = BigDecimal.valueOf(success * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
                if (rate.compareTo(BigDecimal.valueOf(80)) < 0) {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("type", "business_success_rate");
                    alert.put("severity", rate.compareTo(BigDecimal.valueOf(60)) < 0 ? "critical" : "warning");
                    alert.put("restaurantId", entry.getKey());
                    alert.put("shopName", shopNames.get(entry.getKey()));
                    alert.put("totalTransactions", total);
                    alert.put("successRate", rate);
                    alert.put("message", shopNames.get(entry.getKey()) + " has " + rate + "% success rate (" + total + " txns)");
                    results.add(alert);
                }
            }
        }
        return results;
    }

    private List<Map<String, Object>> getPaymentMethodBreakdown(long fromMs, long toMs) {
        List<Object[]> raw = billRepository.countByModeAndStatusBetween(fromMs, toMs);
        Map<String, Map<String, Long>> methodStats = new HashMap<>();
        for (Object[] row : raw) {
            String mode = (String) row[0];
            String status = ((String) row[1]).toLowerCase();
            long count = ((Number) row[2]).longValue();
            methodStats.computeIfAbsent(mode, k -> new HashMap<>())
                    .merge(status, count, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : methodStats.entrySet()) {
            Map<String, Long> stats = entry.getValue();
            long total = stats.values().stream().mapToLong(v -> v).sum();
            long success = stats.getOrDefault("success", 0L) + stats.getOrDefault("paid", 0L);
            BigDecimal rate = total > 0
                    ? BigDecimal.valueOf(success * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> method = new LinkedHashMap<>();
            method.put("method", entry.getKey());
            method.put("total", total);
            method.put("successful", success);
            method.put("successRate", rate);
            result.add(method);
        }
        return result;
    }

    private static long truncateHour(long epochMs) {
        return Instant.ofEpochMilli(epochMs).truncatedTo(ChronoUnit.HOURS).toEpochMilli();
    }

    private static long truncateDay(long epochMs) {
        return Instant.ofEpochMilli(epochMs).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
    }

    private static long truncateWeek(long epochMs) {
        ZonedDateTime dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), IST);
        ZonedDateTime startOfWeek = dt.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(IST);
        return startOfWeek.toInstant().toEpochMilli();
    }
}