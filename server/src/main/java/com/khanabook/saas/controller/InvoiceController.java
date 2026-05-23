package com.khanabook.saas.controller;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final SpringTemplateEngine templateEngine;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DT_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DT_TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    @GetMapping("/pending/{restaurantId}/{deviceId}/{localBillId}/{token}")
    public ResponseEntity<String> getPendingInvoice(
            @PathVariable Long restaurantId,
            @PathVariable String deviceId,
            @PathVariable Long localBillId,
            @PathVariable String token) {

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid token");
        }

        Bill bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalIdAndIsDeletedFalse(
                restaurantId, deviceId, localBillId).orElse(null);
        if (bill == null || !uuid.equals(bill.getPublicToken())) {
            return ResponseEntity.notFound().build();
        }

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElse(null);
        List<BillItem> items = billItemRepository.findByServerBillIdAndIsDeletedFalseOrderById(bill.getId());

        String html = renderInvoice(profile, bill, items);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/{restaurantId}/{billId}/{token}")
    public ResponseEntity<String> getInvoice(
            @PathVariable Long restaurantId,
            @PathVariable Long billId,
            @PathVariable String token) {

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid token");
        }

        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null || !bill.getRestaurantId().equals(restaurantId)
                || !uuid.equals(bill.getPublicToken())
                || Boolean.TRUE.equals(bill.getIsDeleted())) {
            return ResponseEntity.notFound().build();
        }

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElse(null);
        List<BillItem> items = billItemRepository.findByServerBillIdAndIsDeletedFalseOrderById(billId);

        String html = renderInvoice(profile, bill, items);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private String renderInvoice(RestaurantProfile profile, Bill bill, List<BillItem> items) {
        String rawCurrency = (profile != null && profile.getCurrency() != null) ? profile.getCurrency() : "";
        String currencySymbol = rawCurrency.equals("INR") || rawCurrency.equals("Rupee") || rawCurrency.isEmpty() ? "\u20B9" : rawCurrency;

        String shopName = blank(profile != null ? profile.getShopName() : null);
        String address = blank(profile != null ? profile.getShopAddress() : null);
        String phone = blank(profile != null ? profile.getWhatsappNumber() : null);
        String email = blank(profile != null ? profile.getEmail() : null);
        String gstin = blank(profile != null ? profile.getGstin() : null);
        String fssai = blank(profile != null ? profile.getFssaiNumber() : null);
        String logoUrl = blank(profile != null ? profile.getLogoUrl() : null);
        String reviewUrl = blank(profile != null ? profile.getReviewUrl() : null);
        String footer = blank(profile != null ? profile.getInvoiceFooter() : null);
        String customTaxName = blank(profile != null ? profile.getCustomTaxName() : null);

        String dailyOrderDisplay = (bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank())
                ? bill.getDailyOrderDisplay() : "";
        String orderCode = dailyOrderDisplay.isEmpty() ? "ORD" + bill.getLifetimeOrderId() : dailyOrderDisplay;
        String date = bill.getCreatedAt() != null
                ? Instant.ofEpochMilli(bill.getCreatedAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_DATE_FMT) : "";
        String time = bill.getCreatedAt() != null
                ? Instant.ofEpochMilli(bill.getCreatedAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_TIME_FMT) : "";
        String customerName = blank(bill.getCustomerName());
        String customerPhone = blank(bill.getCustomerWhatsapp());
        String paymentMode = formatPayment(bill.getPaymentMode());

        String ps = bill.getPaymentStatus() != null ? bill.getPaymentStatus() : "";
        String paymentStatus = ps.isEmpty() ? "" : ps.substring(0, 1).toUpperCase() + ps.substring(1);

        BigDecimal sub = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
        BigDecimal cgst = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal sgst = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal customTax = bill.getCustomTaxAmount() != null ? bill.getCustomTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal gstPct = bill.getGstPercentage() != null ? bill.getGstPercentage() : BigDecimal.ZERO;
        boolean hasGst = gstPct.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCustomTax = customTax.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal paidAmount = BigDecimal.ZERO;
        if (bill.getPartAmount1() != null) paidAmount = paidAmount.add(bill.getPartAmount1());
        if (bill.getPartAmount2() != null) paidAmount = paidAmount.add(bill.getPartAmount2());
        if (bill.getRefundAmount() != null) paidAmount = paidAmount.subtract(bill.getRefundAmount());
        if (paidAmount.compareTo(BigDecimal.ZERO) < 0) paidAmount = BigDecimal.ZERO;
        BigDecimal balanceDue = total.subtract(paidAmount);
        if (balanceDue.compareTo(BigDecimal.ZERO) < 0) balanceDue = BigDecimal.ZERO;
        boolean isPaid = balanceDue.compareTo(BigDecimal.ZERO) == 0 && paidAmount.compareTo(BigDecimal.ZERO) > 0;
        String paidDate = "";
        if (bill.getPaidAt() != null && bill.getPaidAt() > 0) {
            paidDate = Instant.ofEpochMilli(bill.getPaidAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_FMT);
        }

        String footerMessage = footer.isEmpty() ? "Thank you for your business!" : footer;
        String halfGstPct = hasGst ? DF.format(gstPct.divide(BigDecimal.valueOf(2))) : "";

        List<Map<String, Object>> itemList = new java.util.ArrayList<>();
        for (BillItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemName", blank(item.getItemName()));
            m.put("variantName", blank(item.getVariantName()));
            m.put("quantity", item.getQuantity() != null ? item.getQuantity() : 0);
            m.put("itemTotalFormatted", DF.format(item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO));
            itemList.add(m);
        }

        Context ctx = new Context();
        ctx.setVariable("currencySymbol", currencySymbol);
        ctx.setVariable("shopName", shopName);
        ctx.setVariable("address", address);
        ctx.setVariable("phone", phone);
        ctx.setVariable("email", email);
        ctx.setVariable("gstin", gstin);
        ctx.setVariable("fssai", fssai);
        ctx.setVariable("logoUrl", logoUrl);
        ctx.setVariable("reviewUrl", reviewUrl);
        ctx.setVariable("footerMessage", footerMessage);
        ctx.setVariable("customTaxName", customTaxName.isEmpty() ? "Custom Tax" : customTaxName);
        ctx.setVariable("orderCode", orderCode);
        ctx.setVariable("lifetimeOrderId", bill.getLifetimeOrderId());
        ctx.setVariable("date", date);
        ctx.setVariable("time", time);
        ctx.setVariable("customerName", customerName);
        ctx.setVariable("customerPhone", customerPhone);
        ctx.setVariable("paymentMode", paymentMode);
        ctx.setVariable("paymentIcon", paymentIcon(bill.getPaymentMode()));
        ctx.setVariable("paymentStatus", paymentStatus);
        ctx.setVariable("paidDate", paidDate);
        ctx.setVariable("subtotalFormatted", DF.format(sub));
        ctx.setVariable("cgstFormatted", DF.format(cgst));
        ctx.setVariable("sgstFormatted", DF.format(sgst));
        ctx.setVariable("customTaxFormatted", DF.format(customTax));
        ctx.setVariable("totalFormatted", DF.format(total));
        ctx.setVariable("hasGst", hasGst);
        ctx.setVariable("hasCustomTax", hasCustomTax);
        ctx.setVariable("halfGstPct", halfGstPct);
        ctx.setVariable("isPaid", isPaid);
        ctx.setVariable("items", itemList);

        return templateEngine.process("invoice", ctx);
    }

    private String paymentIcon(String mode) {
        if (mode == null) return "";
        return switch (mode.toLowerCase()) {
            case "cash" -> "\uD83D\uDCB5";
            case "upi" -> "\uD83D\uDCF1";
            case "card", "credit_card", "debit_card" -> "\uD83D\uDCB3";
            case "credit" -> "\uD83D\uDCB3";
            case "online" -> "\uD83C\uDF10";
            case "zomato" -> "\uD83C\uDF7D\uFE0F";
            case "swiggy" -> "\uD83D\uDC69\u200D\uD83C\uDF73";
            case "split" -> "\uD83D\uDCB1";
            default -> "";
        };
    }

    private String formatPayment(String mode) {
        if (mode == null) return "";
        return switch (mode.toLowerCase()) {
            case "cash" -> "Cash";
            case "upi" -> "UPI";
            case "card" -> "Card";
            case "credit" -> "Credit";
            case "credit_card" -> "Credit Card";
            case "debit_card" -> "Debit Card";
            case "online" -> "Online";
            case "zomato" -> "Zomato";
            case "swiggy" -> "Swiggy";
            case "split" -> "Split Payment";
            default -> mode.substring(0, 1).toUpperCase() + mode.substring(1);
        };
    }

    private static String blank(String s) {
        return s == null ? "" : s.strip();
    }
}
