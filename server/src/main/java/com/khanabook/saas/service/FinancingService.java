package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinancingService {

    private static final Logger log = LoggerFactory.getLogger(FinancingService.class);
    private final BillRepository billRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.05"); // 0.05% per day = 1.5% per month = 18% APR
    private static final BigDecimal BASE_CREDIT_MULTIPLIER = new BigDecimal("3");
    private static final boolean IS_SIMULATION = true; // Set to false when integrated with real lending partner

    public Map<String, Object> getCreditEligibility(Long restaurantId) {
        ZonedDateTime now = ZonedDateTime.now(IST);
        long threeMonthsAgo = now.minusMonths(3).toInstant().toEpochMilli();
        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId).stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= threeMonthsAgo)
                .filter(b -> "paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus()))
                .toList();
        BigDecimal threeMonthRevenue = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedCredit = threeMonthRevenue.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP).multiply(BASE_CREDIT_MULTIPLIER).setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxCredit = estimatedCredit.min(new BigDecimal("500000"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("threeMonthRevenue", threeMonthRevenue);
        result.put("monthlyAverage", bills.isEmpty() ? BigDecimal.ZERO : threeMonthRevenue.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
        result.put("estimatedCreditLimit", maxCredit);
        result.put("dailyInterestRate", DAILY_INTEREST_RATE);
        result.put("apr", new BigDecimal("18.00")); // 18% APR
        result.put("totalTransactions", bills.size());
        result.put("eligible", threeMonthRevenue.compareTo(new BigDecimal("10000")) >= 0);
        result.put("isSimulation", IS_SIMULATION);
        result.put("disclaimer", IS_SIMULATION ? "This is an estimate. Actual credit limit subject to partner lender approval." : "");
        return result;
    }

    public Map<String, Object> getLoanOptions(Long restaurantId, BigDecimal requestedAmount) {
        Map<String, Object> eligibility = getCreditEligibility(restaurantId);
        BigDecimal creditLimit = (BigDecimal) eligibility.get("estimatedCreditLimit");
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessRuleException("Amount must be positive");
        if (requestedAmount.compareTo(creditLimit) > 0) throw new BusinessRuleException("Requested amount exceeds credit limit of " + creditLimit);

        List<Map<String, Object>> options = new ArrayList<>();
        int[] terms = {7, 14, 30};
        for (int days : terms) {
            BigDecimal dailyRatePercent = DAILY_INTEREST_RATE; // 0.05%
            BigDecimal totalInterest = requestedAmount.multiply(dailyRatePercent).divide(BigDecimal.valueOf(100)).multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRepayment = requestedAmount.add(totalInterest);
            BigDecimal monthlyEquivalent = totalInterest.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
            options.add(Map.of(
                "days", days,
                "requestedAmount", requestedAmount,
                "interest", totalInterest,
                "totalRepayment", totalRepayment,
                "dailyRate", dailyRatePercent,
                "monthlyEquivalentRate", monthlyEquivalent.divide(requestedAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            ));
        }
        return Map.of("creditLimit", creditLimit, "requestedAmount", requestedAmount, "options", options, "isSimulation", IS_SIMULATION);
    }
}