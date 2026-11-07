package com.khanabook.saas.feature.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingReportData {

    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    private String businessDate;
    private String rawBusinessDate;
    private String openingTime;
    private String closingTime;
    private String cutoffTime;
    private String currency;
    private String generatedAt;

    // High level metrics
    private BigDecimal grossSales;
    private BigDecimal netSales;
    private int totalOrders;
    private int completedOrders;
    private int cancelledOrders;

    // Tax breakdown
    private BigDecimal totalTax;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal customTax;

    // Payment mode breakdown
    private BigDecimal cashTotal;
    private int cashCount;

    private BigDecimal upiTotal;
    private int upiCount;

    private BigDecimal cardTotal;
    private int cardCount;

    private BigDecimal splitTotal;
    private int splitCount;

    private BigDecimal otherTotal;
    private int otherCount;

    // Cashier shifts
    @Builder.Default
    private List<ShiftSummaryItem> shifts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftSummaryItem {
        private String cashierName;
        private String terminal;
        private BigDecimal openingFloat;
        private BigDecimal declaredCash;
        private BigDecimal expectedCash;
        private BigDecimal cashVariance;
        private BigDecimal totalRevenue;
        private Integer totalOrders;
        private String status;
    }
}
