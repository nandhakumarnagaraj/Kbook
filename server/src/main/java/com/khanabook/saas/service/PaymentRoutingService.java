package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRoutingService.class);

    private final EasebuzzWebhookEventRepository eventRepository;
    private final BillRepository billRepository;

    private static final Map<String, RoutingRule> ROUTING_RULES = new LinkedHashMap<>();
    static {
        ROUTING_RULES.put("upi", new RoutingRule("upi", 1, List.of("gpay", "phonepe", "paytm"), 98.5));
        ROUTING_RULES.put("card", new RoutingRule("card", 2, List.of("visa", "mastercard", "rupay"), 95.0));
        ROUTING_RULES.put("netbanking", new RoutingRule("netbanking", 3, List.of("sbi", "hdfc", "icici", "axis"), 92.0));
        ROUTING_RULES.put("wallet", new RoutingRule("wallet", 4, List.of("paytm", "mobikwik", "freecharge"), 94.0));
    }

    public Map<String, Object> getRoutingRecommendations() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(ist);
        long last24h = now.minusHours(24).toInstant().toEpochMilli();
        long last7d = now.minusDays(7).toInstant().toEpochMilli();
        long nowMs = now.toInstant().toEpochMilli();

        long total24h = eventRepository.countByReceivedAtBetween(last24h, nowMs);
        long success24h = eventRepository.countByReceivedAtBetweenAndStatus(last24h, nowMs, "success");
        BigDecimal successRate24h = total24h > 0 ? BigDecimal.valueOf(success24h * 100.0 / total24h).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        long total7d = eventRepository.countByReceivedAtBetween(last7d, nowMs);
        long success7d = eventRepository.countByReceivedAtBetweenAndStatus(last7d, nowMs, "success");
        BigDecimal successRate7d = total7d > 0 ? BigDecimal.valueOf(success7d * 100.0 / total7d).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (successRate24h.compareTo(BigDecimal.valueOf(95)) < 0) {
            recommendations.add(Map.of(
                "type", "warning", "priority", "high",
                "action", "ENABLE_FALLBACK",
                "message", "Gateway success rate dropped to " + successRate24h + "% in last 24h. Consider enabling fallback payment methods.",
                "metric", successRate24h, "threshold", 95
            ));
        }
        if (successRate7d.compareTo(BigDecimal.valueOf(90)) < 0) {
            recommendations.add(Map.of(
                "type", "critical", "priority", "critical",
                "action", "SWITCH_GATEWAY",
                "message", "Weekly success rate is " + successRate7d + "% - investigate gateway health or bank connectivity.",
                "metric", successRate7d, "threshold", 90
            ));
        }

        List<Map<String, Object>> methodRates = getPaymentMethodRates(last24h, nowMs);
        for (Map<String, Object> method : methodRates) {
            BigDecimal rate = (BigDecimal) method.get("successRate");
            String methodName = (String) method.get("method");
            if (rate.compareTo(BigDecimal.valueOf(85)) < 0) {
                recommendations.add(Map.of(
                    "type", "warning", "priority", "medium",
                    "action", "DEPRIORITIZE_METHOD",
                    "message", "Payment method '" + methodName + "' has low success rate: " + rate + "%. Deprioritize in checkout.",
                    "method", methodName, "metric", rate
                ));
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add(Map.of(
                "type", "ok", "priority", "low",
                "action", "NONE",
                "message", "Payment success rates are healthy. No routing changes needed.",
                "metric", successRate24h
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentSuccessRate24h", successRate24h);
        result.put("currentSuccessRate7d", successRate7d);
        result.put("totalTransactions24h", total24h);
        result.put("totalTransactions7d", total7d);
        result.put("recommendations", recommendations);
        result.put("paymentMethodRates", methodRates);
        result.put("routingRules", getActiveRoutingRules());
        return result;
    }

    public Map<String, Object> selectOptimalPaymentMethod(Long restaurantId, BigDecimal amount, String customerVpa) {
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("restaurantId", restaurantId);
        decision.put("amount", amount);

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        long last24h = ZonedDateTime.now(ist).minusHours(24).toInstant().toEpochMilli();
        long nowMs = System.currentTimeMillis();

        List<Map<String, Object>> methodRates = getPaymentMethodRates(last24h, nowMs);
        String optimalMethod = "upi";
        BigDecimal bestRate = BigDecimal.ZERO;

        for (Map<String, Object> method : methodRates) {
            BigDecimal rate = (BigDecimal) method.get("successRate");
            if (rate.compareTo(bestRate) > 0) {
                bestRate = rate;
                optimalMethod = (String) method.get("method");
            }
        }

        if (amount.compareTo(new BigDecimal("2000")) > 0 && "upi".equals(optimalMethod)) {
            optimalMethod = "card";
            decision.put("overrideReason", "High amount (>2000) - card preferred over UPI");
        }

        if (customerVpa != null && customerVpa.contains("@ybl")) {
            decision.put("preferredProvider", "phonepe");
        } else if (customerVpa != null && customerVpa.contains("@okaxis")) {
            decision.put("preferredProvider", "gpay");
        }

        decision.put("recommendedMethod", optimalMethod);
        decision.put("successRate", bestRate);
        decision.put("fallbackMethods", getFallbackMethods(optimalMethod));
        decision.put("timestamp", nowMs);
        return decision;
    }

    private List<Map<String, Object>> getPaymentMethodRates(long fromMs, long toMs) {
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
            method.put("priority", ROUTING_RULES.getOrDefault(entry.getKey(), new RoutingRule(entry.getKey(), 99, List.of(), 0)).priority);
            result.add(method);
        }
        result.sort(Comparator.comparingInt(m -> (int) m.getOrDefault("priority", 99)));
        return result;
    }

    private List<Map<String, String>> getActiveRoutingRules() {
        List<Map<String, String>> rules = new ArrayList<>();
        for (RoutingRule rule : ROUTING_RULES.values()) {
            Map<String, String> r = new LinkedHashMap<>();
            r.put("method", rule.method);
            r.put("priority", String.valueOf(rule.priority));
            r.put("providers", String.join(", ", rule.providers));
            r.put("minSuccessRate", String.valueOf(rule.minSuccessRate));
            rules.add(r);
        }
        return rules;
    }

    private List<String> getFallbackMethods(String primaryMethod) {
        return switch (primaryMethod.toLowerCase()) {
            case "upi" -> List.of("card", "wallet", "netbanking");
            case "card" -> List.of("upi", "wallet", "netbanking");
            case "netbanking" -> List.of("upi", "card", "wallet");
            case "wallet" -> List.of("upi", "card", "netbanking");
            default -> List.of("upi", "card");
        };
    }

    public Map<String, Object> getHistoricalSuccessRates(Long restaurantId) {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.now(ist);
        List<Map<String, Object>> hourlyRates = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            ZonedDateTime from = now.minusHours(i + 1);
            ZonedDateTime to = now.minusHours(i);
            long fromMs = from.toInstant().toEpochMilli();
            long toMs = to.toInstant().toEpochMilli();
            long total = eventRepository.countByReceivedAtBetween(fromMs, toMs);
            long success = eventRepository.countByReceivedAtBetweenAndStatus(fromMs, toMs, "success");
            BigDecimal rate = total > 0 ? BigDecimal.valueOf(success * 100.0 / total).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            hourlyRates.add(Map.of("hour", from.getHour(), "rate", rate, "total", total, "success", success));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("hourlyRates", hourlyRates);
        return result;
    }

    private record RoutingRule(String method, int priority, List<String> providers, double minSuccessRate) {}
}