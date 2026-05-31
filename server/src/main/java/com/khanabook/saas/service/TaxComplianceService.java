package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxComplianceService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final RestaurantProfileRepository profileRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public Map<String, Object> getGstReport(Long restaurantId, int year, int month) {
        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BusinessRuleException("Business not found"));

        ZonedDateTime monthStart = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, IST);
        ZonedDateTime monthEnd = monthStart.plusMonths(1);
        long fromMs = monthStart.toInstant().toEpochMilli();
        long toMs = monthEnd.toInstant().toEpochMilli();

        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId).stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= fromMs && b.getCreatedAt() < toMs)
                .filter(b -> !"deleted".equals(b.getOrderStatus()) && !"cancelled".equals(b.getOrderStatus()))
                .toList();

        BigDecimal taxableAmount = bills.stream()
                .map(b -> b.getSubtotal() != null ? b.getSubtotal() : b.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCgst = bills.stream()
                .map(b -> b.getCgstAmount() != null ? b.getCgstAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSgst = bills.stream()
                .map(b -> b.getSgstAmount() != null ? b.getSgstAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = totalCgst.add(totalSgst);
        BigDecimal totalRevenue = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("restaurantId", restaurantId);
        report.put("shopName", profile.getShopName());
        report.put("gstin", profile.getGstin());
        report.put("period", String.format("%d-%02d", year, month));
        report.put("totalOrders", bills.size());
        report.put("taxableAmount", taxableAmount);
        report.put("totalCgst", totalCgst);
        report.put("totalSgst", totalSgst);
        report.put("totalTax", totalTax);
        report.put("totalRevenue", totalRevenue);
        report.put("taxRate", profile.getGstPercentage());
        return report;
    }

    public String generateGstReportCsv(Long restaurantId, int year, int month) {
        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BusinessRuleException("Business not found"));

        ZonedDateTime monthStart = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, IST);
        ZonedDateTime monthEnd = monthStart.plusMonths(1);
        long fromMs = monthStart.toInstant().toEpochMilli();
        long toMs = monthEnd.toInstant().toEpochMilli();

        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId).stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= fromMs && b.getCreatedAt() < toMs)
                .filter(b -> !"deleted".equals(b.getOrderStatus()) && !"cancelled".equals(b.getOrderStatus()))
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("Invoice Date,Invoice Number,Customer Name,Subtotal,CGST,SGST,Total Tax,Total Amount,Payment Mode\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(IST);
        for (Bill bill : bills) {
            csv.append(fmt.format(Instant.ofEpochMilli(bill.getCreatedAt())))
               .append(",")
               .append(bill.getPublicToken() != null ? bill.getPublicToken() : "")
               .append(",")
               .append(bill.getCustomerName() != null ? escapeCsv(bill.getCustomerName()) : "")
               .append(",")
               .append(bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO)
               .append(",")
               .append(bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO)
               .append(",")
               .append(bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO)
               .append(",")
               .append(bill.getCgstAmount() != null ? bill.getCgstAmount().add(bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO) : BigDecimal.ZERO)
               .append(",")
               .append(bill.getTotalAmount())
               .append(",")
               .append(bill.getPaymentMode() != null ? bill.getPaymentMode() : "")
               .append("\n");
        }

        csv.append("\nSummary\n");
        csv.append("Shop Name,").append(profile.getShopName()).append("\n");
        csv.append("GSTIN,").append(profile.getGstin() != null ? profile.getGstin() : "N/A").append("\n");
        csv.append("Period,").append(String.format("%d-%02d", year, month)).append("\n");
        csv.append("Total Orders,").append(bills.size()).append("\n");

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public Map<String, Object> getGstReturnData(Long restaurantId, int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        BigDecimal qTaxable = BigDecimal.ZERO;
        BigDecimal qCgst = BigDecimal.ZERO;
        BigDecimal qSgst = BigDecimal.ZERO;
        List<Map<String, Object>> monthlyReports = new ArrayList<>();
        for (int m = startMonth; m < startMonth + 3; m++) {
            Map<String, Object> monthReport = getGstReport(restaurantId, year, m);
            monthlyReports.add(monthReport);
            qTaxable = qTaxable.add((BigDecimal) monthReport.get("taxableAmount"));
            qCgst = qCgst.add((BigDecimal) monthReport.get("totalCgst"));
            qSgst = qSgst.add((BigDecimal) monthReport.get("totalSgst"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quarter", "Q" + quarter + " " + year);
        result.put("totalTaxable", qTaxable);
        result.put("totalCgst", qCgst);
        result.put("totalSgst", qSgst);
        result.put("totalTax", qCgst.add(qSgst));
        result.put("monthlyBreakdown", monthlyReports);
        return result;
    }

    public Map<String, Object> getTaxSummary(Long restaurantId) {
        LocalDate today = LocalDate.now(IST);
        ZonedDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(IST);
        ZonedDateTime yearStart = today.withDayOfYear(1).atStartOfDay(IST);
        long monthFrom = monthStart.toInstant().toEpochMilli();
        long yearFrom = yearStart.toInstant().toEpochMilli();
        long now = System.currentTimeMillis();

        List<Bill> allBills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        BigDecimal monthRevenue = allBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= monthFrom)
                .filter(b -> "paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus()))
                .map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal yearRevenue = allBills.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt() >= yearFrom)
                .filter(b -> "paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus()))
                .map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId).orElse(null);
        BigDecimal taxRate = profile != null && profile.getGstPercentage() != null ? profile.getGstPercentage() : BigDecimal.ZERO;
        BigDecimal monthTax = monthRevenue.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal yearTax = yearRevenue.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("restaurantId", restaurantId);
        summary.put("gstin", profile != null ? profile.getGstin() : null);
        summary.put("taxRate", taxRate);
        summary.put("monthRevenue", monthRevenue);
        summary.put("monthTax", monthTax);
        summary.put("yearRevenue", yearRevenue);
        summary.put("yearTax", yearTax);
        return summary;
    }
}