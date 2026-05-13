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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/public/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DT_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DT_TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

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

        String html = buildHtml(profile, bill, items);
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

        String html = buildHtml(profile, bill, items);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private String buildHtml(RestaurantProfile profile, Bill bill, List<BillItem> items) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String raw = (profile != null && profile.getCurrency() != null) ? profile.getCurrency() : "";
        String currency = raw.equals("INR") || raw.equals("Rupee") || raw.isEmpty() ? "\u20B9" : raw;

        String shopName = profile != null ? blank(profile.getShopName()) : "Shop";
        String address = profile != null ? blank(profile.getShopAddress()) : "";
        String phone = profile != null ? blank(profile.getWhatsappNumber()) : "";
        String email = profile != null ? blank(profile.getEmail()) : "";
        String gstin = profile != null ? blank(profile.getGstin()) : "";
        String fssai = profile != null ? blank(profile.getFssaiNumber()) : "";
        String logoUrl = profile != null ? blank(profile.getLogoUrl()) : "";
        String reviewUrl = profile != null ? blank(profile.getReviewUrl()) : "";
        String footer = profile != null ? blank(profile.getInvoiceFooter()) : "";
        String customTaxName = profile != null ? blank(profile.getCustomTaxName()) : "";

        String dailyOrderDisplay = (bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank())
                ? bill.getDailyOrderDisplay() : "";
        String orderCode = dailyOrderDisplay.isEmpty() ? "INV" + bill.getLifetimeOrderId() : dailyOrderDisplay;
        String date = bill.getCreatedAt() != null
                ? Instant.ofEpochMilli(bill.getCreatedAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_FMT)
                : "";
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
        boolean showWatermark = isPaid || ps.equals("paid") || ps.equals("completed") || ps.equals("success");
        String paidDate = "";
        if (bill.getPaidAt() != null && bill.getPaidAt() > 0) {
            paidDate = Instant.ofEpochMilli(bill.getPaidAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_FMT);
        }

        StringBuilder rows = new StringBuilder();
        int rowIdx = 0;
        for (BillItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            String name = blank(item.getItemName());
            String variant = blank(item.getVariantName());
            String label;
            if (variant.isEmpty()) {
                label = esc(name);
            } else {
                label = esc(name) + " <span style=\"color:#64748b;font-size:0.85em\">(" + esc(variant) + ")</span>";
            }
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            String price = df.format(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
            String lineTotal = df.format(item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO);
            String itemTax = "";
            if (hasGst) {
                BigDecimal itemTotalVal = item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO;
                BigDecimal taxAmt = itemTotalVal.multiply(gstPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                itemTax = df.format(taxAmt);
            }
            String cls = rowIdx % 2 == 0 ? "" : " class=\"e\"";
            rows.append("<tr").append(cls).append(">")
                .append("<td>").append(label).append("</td>")
                .append("<td class=\"c\">").append(qty).append("</td>")
                .append("<td class=\"r\">").append(currency).append(" ").append(lineTotal).append("</td>")
                .append("</tr>");
            rowIdx++;
        }

        String balanceStr = balanceDue.compareTo(BigDecimal.ZERO) > 0 ? currency + " " + df.format(balanceDue) : "";
        String paidStr = paidAmount.compareTo(BigDecimal.ZERO) > 0 ? currency + " " + df.format(paidAmount) : "";
        String totalStr = currency + " " + df.format(total);
        String psLabel = paymentStatus.isEmpty() ? "" : paymentStatus;
        String psClass = ps.isEmpty() ? "" : ps.toLowerCase();
        String psLabelDisplay = psLabel.isEmpty() ? "Completed" : psLabel;

        String upiQr = (profile != null && profile.getUpiQrUrl() != null && !profile.getUpiQrUrl().isBlank())
                ? profile.getUpiQrUrl() : "";
        String upiHandle = (profile != null && profile.getUpiHandle() != null && !profile.getUpiHandle().isBlank())
                ? profile.getUpiHandle() : "";
        String upiMobile = (profile != null && profile.getUpiMobile() != null && !profile.getUpiMobile().isBlank())
                ? profile.getUpiMobile() : "";

        StringBuilder h = new StringBuilder(8192);

        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
         .append("<title>Invoice - ").append(esc(shopName)).append("</title>")
         .append("<style>");

        // ═══════════════════════════════════════════
        // FONT + RESET
        // ═══════════════════════════════════════════
        h.append(
        "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');"
        + "*,:after,:before{margin:0;padding:0;box-sizing:border-box}"
        + "body{"
        + "font-family:'Inter',system-ui,-apple-system,sans-serif;"
        + "background:#f3f3f3;color:#111;padding:24px;min-height:100vh;"
        + "display:flex;align-items:flex-start;justify-content:center;"
        + "line-height:1.5;-webkit-font-smoothing:antialiased"
        + "}"
        + ".pg{"
        + "max-width:580px;width:100%;margin:0 auto;background:#fff;"
        + "border-radius:12px;box-shadow:0 1px 4px rgba(0,0,0,.06);"
        + "padding:32px 28px 20px"
        + "}");

        // ═══════════════════════════════════════════
        // UTILITY
        // ═══════════════════════════════════════════
        h.append(
        ".mt8{margin-top:8px}.mt12{margin-top:12px}.mt16{margin-top:16px}.mt20{margin-top:20px}.mt24{margin-top:24px}"
        + ".mb8{margin-bottom:8px}.mb12{margin-bottom:12px}.mb16{margin-bottom:16px}"
        + ".sep{height:1px;background:#ededed;margin:14px 0}"
        + ".text-muted{color:#777}.text-sm{font-size:.78em}"
        + ".fw600{font-weight:600}");

        // ═══════════════════════════════════════════
        // STATUS BADGE
        // ═══════════════════════════════════════════
        h.append(
        ".bdg{display:inline-flex;align-items:center;gap:4px;padding:2px 10px;"
        + "border-radius:10px;font-size:.7em;font-weight:600;line-height:1.4}"
        + ".bdg-dot{width:5px;height:5px;border-radius:50%;display:inline-block}"
        + ".bdg-paid,.bdg-completed,.bdg-success{background:#ecfdf5;color:#065f46}"
        + ".bdg-paid .bdg-dot,.bdg-completed .bdg-dot,.bdg-success .bdg-dot{background:#059669}"
        + ".bdg-pending,.bdg-unpaid,.bdg-partial,.bdg-partially_paid{background:#fffbeb;color:#92400e}"
        + ".bdg-pending .bdg-dot,.bdg-unpaid .bdg-dot,.bdg-partial .bdg-dot,.bdg-partially_paid .bdg-dot{background:#d97706}"
        + ".bdg-cancelled,.bdg-failed,.bdg-overdue{background:#fef2f2;color:#991b1b}"
        + ".bdg-cancelled .bdg-dot,.bdg-failed .bdg-dot,.bdg-overdue .bdg-dot{background:#dc2626}"
        + ".bdg-draft,.bdg-refunded{background:#f5f5f5;color:#666}"
        + ".bdg-draft .bdg-dot,.bdg-refunded .bdg-dot{background:#999}");

        // ═══════════════════════════════════════════
        // HEADER — centered logo + business
        // ═══════════════════════════════════════════
        h.append(
        ".hdr{text-align:center;margin-bottom:14px}"
        + ".logo{width:80px;height:80px;border-radius:12px;object-fit:contain;"
        + "border:1px solid #e5e5e5;background:#fff;padding:4px;margin-bottom:10px}"
        + ".biz-name{font-size:1.15em;font-weight:700;color:#111;margin-bottom:3px}"
        + ".biz-info{font-size:.78em;color:#666;line-height:1.6}");

        // ═══════════════════════════════════════════
        // META — two-column key-value
        // ═══════════════════════════════════════════
        h.append(
        ".meta{display:grid;grid-template-columns:1fr 1fr;gap:4px 24px;padding:10px 0;font-size:.82em}"
        + ".meta-i{display:flex;justify-content:space-between;padding:3px 0}"
        + ".meta-i .l{color:#999;font-size:.7em;font-weight:600}"
        + ".meta-i .v{color:#111;font-weight:500}");

        // ═══════════════════════════════════════════
        // ADDRESS CARDS
        // ═══════════════════════════════════════════
        h.append(
        ".addr{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin:10px 0}"
        + ".addr .ttl{font-size:.64em;text-transform:uppercase;letter-spacing:.04em;color:#999;font-weight:600;margin-bottom:3px}"
        + ".addr .ct{font-size:.78em;color:#444;line-height:1.6}"
        + ".addr .ct strong{color:#111;font-weight:600}");

        // ═══════════════════════════════════════════
        // TABLE
        // ═══════════════════════════════════════════
        h.append(
        "table{width:100%;border-collapse:collapse;font-size:.82em}"
        + "thead th{padding:7px 4px;font-size:.64em;text-transform:uppercase;letter-spacing:.04em;"
        + "color:#999;font-weight:600;border-bottom:1px solid #ddd;text-align:left}"
        + "thead th.r{text-align:right}"
        + "thead th.c{text-align:center}"
        + "tbody tr{border-bottom:1px solid #eee}"
        + "tbody tr:last-child{border-bottom:none}"
        + "tbody tr.e{background:#fafafa}"
        + "tbody td{padding:9px 4px;color:#333;vertical-align:top;font-size:.85em}"
        + "tbody td:first-child{padding-left:0}"
        + "tbody td:last-child{padding-right:0}"
        + "tbody td.c{text-align:center;color:#666}"
        + "tbody td.r{text-align:right;font-weight:500;font-variant-numeric:tabular-nums}");

        // ═══════════════════════════════════════════
        // SUMMARY
        // ═══════════════════════════════════════════
        h.append(
        ".sum{margin-top:10px;padding-top:6px}"
        + ".sum-r{display:flex;justify-content:space-between;padding:4px 0;font-size:.84em;color:#555}"
        + ".sum-d{height:1px;background:#ddd;margin:7px 0}"
        + ".sum-t{display:flex;justify-content:space-between;padding:8px 0 0;"
        + "font-size:1.1em;font-weight:700;color:#111;border-top:2px solid #111;margin-top:3px}");

        // ═══════════════════════════════════════════
        // PAYMENT
        // ═══════════════════════════════════════════
        h.append(
        ".pay{font-size:.82em;color:#555;padding:6px 0}"
        + ".pay strong{color:#111}");

        // ═══════════════════════════════════════════
        // FOOTER MESSAGE
        // ═══════════════════════════════════════════
        h.append(
        ".fmsg{font-size:.82em;color:#555;padding:6px 0;line-height:1.6}"
        + ".fmsg strong{color:#111}");

        // ═══════════════════════════════════════════
        // REVIEW
        // ═══════════════════════════════════════════
        h.append(
        ".rev{text-align:center;font-size:.8em;color:#888;padding:10px 0 0}"
        + ".rev a{color:#111;font-weight:600;text-decoration:none}");

        // ═══════════════════════════════════════════
        // FOOTER
        // ═══════════════════════════════════════════
        h.append(
        ".ft{padding:10px 0 0;text-align:center;font-size:.7em;color:#aaa;"
        + "border-top:1px solid #eee;margin-top:14px}"
        + ".ft a{color:#555;font-weight:500;text-decoration:none}");

        // ═══════════════════════════════════════════
        // PRINT
        // ═══════════════════════════════════════════
        h.append(
        "@media print{"
        + "@page{margin:0}"
        + "body{background:#fff!important;padding:0!important;display:block}"
        + ".pg{box-shadow:none!important;border-radius:0!important;max-width:100%!important;padding:24px 20px 16px!important}"
        + ".bdg-paid,.bdg-completed,.bdg-success,.bdg-pending,.bdg-cancelled,.bdg-failed,.bdg-draft,.bdg-refunded"
        + "{-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important}"
        + "}");

        // ═══════════════════════════════════════════
        // MOBILE
        // ═══════════════════════════════════════════
        h.append(
        "@media(max-width:480px){"
        + "body{padding:10px}"
        + ".pg{padding:20px 14px 16px;border-radius:8px}"
        + ".meta{flex-direction:column;gap:6px}"
        + ".addr{grid-template-columns:1fr;gap:10px}"
        + "tbody td{font-size:.8em;padding:7px 3px}"
        + "thead th{font-size:.6em;padding:6px 3px}"
        + "}"
        + "</style></head><body>");

        // ══════════════════════════════════════════════════════
        // PAGE CARD
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"pg\">");

        // ══════════════════════════════════════════════════════
        // HEADER: centered logo + business details
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"hdr\">");
        if (!logoUrl.isEmpty()) {
            h.append("<div><img class=\"logo\" src=\"").append(esc(logoUrl))
             .append("\" alt=\"").append(esc(shopName)).append("\" onerror=\"this.style.display='none'\"></div>");
        }
        h.append("<div class=\"biz-name\">").append(esc(shopName)).append("</div>")
         .append("<div class=\"biz-info\">");
        if (!address.isEmpty()) h.append(esc(address)).append("<br>");
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        if (!gstin.isEmpty()) ids.add("GST: " + esc(gstin));
        if (!fssai.isEmpty()) ids.add("FSSAI: " + esc(fssai));
        if (!ids.isEmpty()) h.append(String.join(" &middot; ", ids)).append("<br>");
        if (!phone.isEmpty() || !email.isEmpty()) {
            if (!phone.isEmpty()) h.append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" | ");
            if (!email.isEmpty()) h.append(esc(email));
        }
        h.append("</div></div>");

        // ══════════════════════════════════════════════════════
        // DIVIDER
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>");

        // ══════════════════════════════════════════════════════
        // META: Order ID, Invoice No, Date, Time (2-column grid)
        // ══════════════════════════════════════════════════════
        String orderIdStr = dailyOrderDisplay.isEmpty() ? "ORD" + bill.getLifetimeOrderId() : dailyOrderDisplay;
        String invoiceNoStr = "INV" + bill.getLifetimeOrderId();
        String dateShort = bill.getCreatedAt() != null
                ? Instant.ofEpochMilli(bill.getCreatedAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_DATE_FMT) : "";
        String timeStr = bill.getCreatedAt() != null
                ? Instant.ofEpochMilli(bill.getCreatedAt()).atZone(ZoneId.of("Asia/Kolkata")).format(DT_TIME_FMT) : "";

        h.append("<div class=\"meta\">")
         .append("<div class=\"meta-i\"><span class=\"l\">Order ID</span><span class=\"v\">").append(esc(orderIdStr)).append("</span></div>")
         .append("<div class=\"meta-i\"><span class=\"l\">Invoice No</span><span class=\"v\">").append(esc(invoiceNoStr)).append("</span></div>")
         .append("<div class=\"meta-i\"><span class=\"l\">Date</span><span class=\"v\">").append(esc(dateShort)).append("</span></div>")
         .append("<div class=\"meta-i\"><span class=\"l\">Time</span><span class=\"v\">").append(esc(timeStr)).append("</span></div>")
         .append("</div>");

        // ══════════════════════════════════════════════════════
        // ADDRESSES: From / Bill To
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"addr\">")
         .append("<div><div class=\"ttl\">From</div>")
         .append("<div class=\"ct\"><strong>").append(esc(shopName)).append("</strong><br>");
        if (!address.isEmpty()) h.append(esc(address)).append("<br>");
        if (!gstin.isEmpty()) h.append("GST: ").append(esc(gstin)).append("<br>");
        if (!phone.isEmpty() || !email.isEmpty()) {
            if (!phone.isEmpty()) h.append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" | ");
            if (!email.isEmpty()) h.append(esc(email));
        }
        h.append("</div></div>")
         .append("<div><div class=\"ttl\">Bill To</div>")
         .append("<div class=\"ct\">");
        if (!customerName.isEmpty()) {
            h.append("<strong>").append(esc(customerName)).append("</strong><br>");
        } else {
            h.append("<strong>Guest</strong><br>");
        }
        if (!customerPhone.isEmpty()) h.append(esc(customerPhone)).append("<br>");
        h.append("</div></div></div>");

        // ══════════════════════════════════════════════════════
        // DIVIDER
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>");

        // ══════════════════════════════════════════════════════
        // ITEMS TABLE: Item, Qty, Total only
        // ══════════════════════════════════════════════════════
        h.append("<table><thead><tr>")
         .append("<th style=\"width:58%\">Item</th><th class=\"c\" style=\"width:15%\">Qty</th>")
         .append("<th class=\"r\" style=\"width:27%\">Total</th>")
         .append("</tr></thead><tbody>").append(rows)
         .append("</tbody></table>");

        // ══════════════════════════════════════════════════════
        // SUMMARY: Subtotal, CGST, SGST, Total
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"sum\">")
         .append("<div class=\"sum-r\"><span>Subtotal</span><span>").append(currency).append(" ").append(df.format(sub)).append("</span></div>");
        if (hasGst) {
            String half = df.format(gstPct.divide(BigDecimal.valueOf(2)));
            h.append("<div class=\"sum-r\"><span>CGST @").append(half).append("%</span><span>").append(currency).append(" ").append(df.format(cgst)).append("</span></div>")
             .append("<div class=\"sum-r\"><span>SGST @").append(half).append("%</span><span>").append(currency).append(" ").append(df.format(sgst)).append("</span></div>");
        }
        if (hasCustomTax) {
            String label = customTaxName.isEmpty() ? "Custom Tax" : esc(customTaxName);
            h.append("<div class=\"sum-r\"><span>").append(label).append("</span><span>").append(currency).append(" ").append(df.format(customTax)).append("</span></div>");
        }
        h.append("<div class=\"sum-d\"></div>")
         .append("<div class=\"sum-t\"><span>Total</span><span>").append(totalStr).append("</span></div>")
         .append("</div>");

        // ══════════════════════════════════════════════════════
        // PAYMENT
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>")
         .append("<div class=\"pay\">")
         .append(paymentIcon(paymentMode)).append(" <strong>").append(esc(paymentMode)).append("</strong>");
        if (isPaid && !paidDate.isEmpty()) {
            h.append(" &middot; Paid on ").append(esc(paidDate));
        }
        h.append("</div>");

        // ══════════════════════════════════════════════════════
        // FOOTER MESSAGE
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>")
         .append("<div class=\"fmsg\">")
         .append(esc(footer.isEmpty() ? "Thank you for your business!" : footer))
         .append("</div>");

        // ══════════════════════════════════════════════════════
        // REVIEW CTA
        // ══════════════════════════════════════════════════════
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"rev\">")
             .append("\u2B50 How was your experience? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // ══════════════════════════════════════════════════════
        // FOOTER
        // ══════════════════════════════════════════════════════
        h.append("<div class=\"ft\">")
         .append("Powered by <a href=\"https://play.google.com/store/apps/details?id=com.piquantservices.khanabooklite\" target=\"_blank\">KhanaBook</a>")
         .append("</div>")
         .append("</div></body></html>");

        return h.toString();
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

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String blank(String s) {
        return s == null ? "" : s.strip();
    }
}
