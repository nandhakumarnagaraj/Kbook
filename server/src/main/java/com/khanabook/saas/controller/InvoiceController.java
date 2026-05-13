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
        String os = bill.getOrderStatus() != null ? bill.getOrderStatus() : "";
        String paymentStatus = ps.isEmpty() ? "" : ps.substring(0, 1).toUpperCase() + ps.substring(1);
        String orderStatus = os.isEmpty() ? "" : os.substring(0, 1).toUpperCase() + os.substring(1);

        BigDecimal sub = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
        BigDecimal cgst = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal sgst = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal customTax = bill.getCustomTaxAmount() != null ? bill.getCustomTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal gstPct = bill.getGstPercentage() != null ? bill.getGstPercentage() : BigDecimal.ZERO;
        boolean hasGst = gstPct.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCustomTax = customTax.compareTo(BigDecimal.ZERO) > 0;
        boolean hasPartPayments = bill.getPartAmount1() != null || bill.getPartAmount2() != null;

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
            String cls = rowIdx % 2 == 0 ? "" : " class=\"e\"";
            rows.append("<tr").append(cls).append(">")
                .append("<td class=\"n\">").append(label).append("</td>")
                .append("<td class=\"q\">").append(qty).append("</td>")
                .append("<td class=\"p\">").append(price).append("</td>")
                .append("<td class=\"t\">").append(lineTotal).append("</td>")
                .append("</tr>");
            rowIdx++;
        }

        StringBuilder h = new StringBuilder(6144);

        // === HEAD ===
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
         .append("<title>Invoice - ").append(esc(shopName)).append("</title>")
         .append("<style>");

        // === CSS ===
        h.append(
        "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');"
        + "*{margin:0;padding:0;box-sizing:border-box}"
        + "body{"
        +   "font-family:'Inter',-apple-system,system-ui,sans-serif;"
        +   "background:#f1f5f9;color:#0f172a;padding:24px;min-height:100vh;"
        +   "display:flex;align-items:center;justify-content:center"
        + "}"
        + ".page{"
        +   "max-width:820px;width:100%;margin:0 auto;background:#fff;"
        +   "border-radius:20px;box-shadow:0 1px 3px rgba(0,0,0,.04),0 8px 32px rgba(0,0,0,.08);"
        +   "overflow:hidden"
        + "}");

        // Header
        h.append(
        ".hdr{background:#0f172a;padding:36px 40px 32px;text-align:center}"
        + ".hdr .logo{width:100px;height:100px;border-radius:14px;object-fit:contain;"
        +   "border:3px solid rgba(255,255,255,.25);background:#fff;padding:6px;margin-bottom:14px}"
        + ".hdr h1{color:#fff;font-size:1.4em;font-weight:700;letter-spacing:-.02em}"
        + ".hdr .sub{color:#94a3b8;font-size:.82em;margin-top:6px;line-height:1.6;max-width:520px;margin-left:auto;margin-right:auto}");

        // Ribbon
        h.append(
        ".rib{display:grid;grid-template-columns:1fr 1fr 1fr;"
        +   "padding:18px 40px;border-bottom:1px solid #e9edf2;background:#fafbfc}"
        + ".rib .c{font-size:.8em;color:#64748b;line-height:1.5}"
        + ".rib .c .v{display:block;color:#0f172a;font-size:1.05em;font-weight:600;margin-top:1px}");

        // Customer
        h.append(
        ".cust{display:flex;align-items:center;gap:10px;flex-wrap:wrap;"
        +   "padding:14px 40px;border-bottom:1px solid #e9edf2;font-size:.88em;color:#64748b}"
        + ".cust .v{color:#0f172a;font-weight:500}");

        // Table
        h.append(
        ".tw{padding:6px 32px 0}"
        + "table{width:100%;border-collapse:collapse}"
        + "thead th{padding:14px 10px;font-size:.7em;text-transform:uppercase;letter-spacing:.06em;"
        +   "color:#64748b;font-weight:600;border-bottom:2px solid #e9edf2;text-align:left}"
        + "thead th.r{text-align:right}thead th.c{text-align:center}"
        + "tbody tr{border-bottom:1px solid #f1f4f9}"
        + "tbody tr:last-child{border-bottom:none}"
        + "tbody tr.e{background:#f8fafc}"
        + "tbody td{padding:12px 10px;font-size:.88em;color:#1e293b;vertical-align:top}"
        + "tbody td.q{text-align:center;color:#475569}"
        + "tbody td.p{text-align:right;color:#475569}"
        + "tbody td.t{text-align:right;font-weight:600}");

        // Totals
        h.append(
        ".tot{margin:0 32px;padding:14px 10px 10px;border-top:2px solid #e9edf2}"
        + ".tot table{width:auto;margin-left:auto}"
        + ".tot td{padding:5px 0 5px 24px;font-size:.8em}"
        + ".tot td.l{text-align:right;color:#64748b}"
        + ".tot td.a{text-align:right;font-weight:500;min-width:110px;color:#1e293b}"
        + ".tot .gr td{padding-top:10px;font-size:1em;font-weight:700;color:#0f172a;border-top:2px solid #0f172a}");

        // Payment
        h.append(
        ".pay{margin:0 32px 4px;padding:14px 10px;border-top:1px solid #e9edf2;"
        +   "display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px;"
        +   "font-size:.85em;color:#64748b}");

        // Review
        h.append(
        ".rev{margin:0 32px 0;padding:14px 10px;text-align:center;"
        +   "background:#fef9ef;border:1px solid #fde68a;border-radius:12px;font-size:.85em;color:#92400e}"
        + ".rev a{color:#2563eb;font-weight:600;text-decoration:none}"
        + ".rev a:hover{text-decoration:underline}");

        // Footer
        h.append(
        ".ft{margin-top:18px;padding:20px 40px 28px;text-align:center;font-size:.8em;"
        +   "color:#94a3b8;border-top:1px solid #e9edf2}");

        // Print
        h.append(
        "@media print{"
        + "body{background:#fff!important;padding:0;display:block;min-height:auto}"
        + ".page{box-shadow:none!important;border-radius:0!important;max-width:100%!important}"
        + ".hdr{background:#0f172a!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        + ".rev,.badge-completed,.badge-cancelled,.badge-draft,.badge-paid,.badge-success,.badge-pending,.badge-failed,.badge-refunded"
        + "{-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        + "tbody tr.e{background:#f8fafc!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        + "}");

        // Mobile
        h.append(
        "@media(max-width:640px){"
        + "body{padding:10px}"
        + ".hdr{padding:24px 18px 22px}"
        + ".hdr .logo{width:80px;height:80px;margin-bottom:10px}"
        + ".hdr h1{font-size:1.2em}"
        + ".rib{grid-template-columns:1fr;gap:8px;padding:14px 18px;text-align:left}"
        + ".rib .c{display:flex;gap:6px;font-size:.82em}"
        + ".rib .c .v{display:inline;font-size:.95em}"
        + ".cust{padding:12px 18px}"
        + ".tw{padding:4px 16px 0;overflow-x:auto}"
        + "table{min-width:360px}"
        + "thead th{padding:10px 6px;font-size:.65em}"
        + "tbody td{padding:10px 6px;font-size:.84em}"
        + ".tot{margin:0 16px;padding:12px 6px 8px}"
        + ".tot td{padding:4px 0 4px 12px;font-size:.78em}"
        + ".pay{margin:0 16px 2px;padding:10px 6px;font-size:.8em}"
        + ".rev{margin:0 16px;padding:12px 6px}"
        + ".ft{padding:16px 18px 22px}"
        + "}"
        + "</style></head><body><div class=\"page\">");

        // === HEADER ===
        h.append("<div class=\"hdr\">");
        if (!logoUrl.isEmpty()) {
            h.append("<div><img class=\"logo\" src=\"").append(esc(logoUrl))
             .append("\" alt=\"").append(esc(shopName)).append("\" onerror=\"this.style.display='none'\"></div>");
        }
        h.append("<h1>").append(esc(shopName)).append("</h1><div class=\"sub\">");
        if (!address.isEmpty()) {
            h.append(esc(address)).append("<br>");
        }
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        if (!gstin.isEmpty()) ids.add("GST: " + esc(gstin));
        if (!fssai.isEmpty()) ids.add("FSSAI: " + esc(fssai));
        if (!ids.isEmpty()) {
            h.append(String.join(" &middot; ", ids)).append("<br>");
        }
        if (!phone.isEmpty() || !email.isEmpty()) {
            h.append("<span style=\"color:#cbd5e1\">");
            if (!phone.isEmpty()) h.append("\u260E ").append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" &nbsp;|&nbsp; ");
            if (!email.isEmpty()) h.append("\u2709 ").append(esc(email));
            h.append("</span>");
        }
        h.append("</div></div>");

        // === RIBBON ===
        h.append("<div class=\"rib\">")
         .append("<div class=\"c\">Order No<span class=\"v\">").append(esc(orderCode)).append("</span></div>")
         .append("<div class=\"c\" style=\"text-align:center\">Invoice No<span class=\"v\">INV").append(bill.getLifetimeOrderId()).append("</span></div>")
         .append("<div class=\"c\" style=\"text-align:right\">Date<span class=\"v\">").append(esc(date)).append("</span></div>")
         .append("</div>");

        // === CUSTOMER ===
        if (!customerName.isEmpty() || !customerPhone.isEmpty()) {
            h.append("<div class=\"cust\">")
             .append("\uD83D\uDC64 Bill to:");
            if (!customerName.isEmpty()) h.append("<span class=\"v\">").append(esc(customerName)).append("</span>");
            if (!customerName.isEmpty() && !customerPhone.isEmpty()) h.append("<span style=\"color:#d1d5db\">|</span>");
            if (!customerPhone.isEmpty()) h.append("<span class=\"v\">").append(esc(customerPhone)).append("</span>");
            h.append("</div>");
        }

        // === TABLE ===
        h.append("<div class=\"tw\"><table><thead><tr>")
         .append("<th style=\"width:44%\">Item</th><th class=\"c\" style=\"width:10%\">Qty</th>")
         .append("<th class=\"r\" style=\"width:20%\">Price</th><th class=\"r\" style=\"width:26%\">Total</th>")
         .append("</tr></thead><tbody>").append(rows)
         .append("</tbody></table></div>");

        // === TOTALS ===
        h.append("<div class=\"tot\"><table>")
         .append("<tr><td class=\"l\">Subtotal</td><td class=\"a\">").append(currency).append(" ").append(df.format(sub)).append("</td></tr>");
        if (hasGst) {
            String half = df.format(gstPct.divide(BigDecimal.valueOf(2)));
            h.append("<tr><td class=\"l\">CGST @").append(half).append("%</td><td class=\"a\">").append(currency).append(" ").append(df.format(cgst)).append("</td></tr>")
             .append("<tr><td class=\"l\">SGST @").append(half).append("%</td><td class=\"a\">").append(currency).append(" ").append(df.format(sgst)).append("</td></tr>");
        }
        if (hasCustomTax) {
            String label = customTaxName.isEmpty() ? "Custom Tax" : esc(customTaxName);
            h.append("<tr><td class=\"l\">").append(label).append("</td><td class=\"a\">").append(currency).append(" ").append(df.format(customTax)).append("</td></tr>");
        }
        if (hasPartPayments) {
            BigDecimal p1 = bill.getPartAmount1();
            BigDecimal p2 = bill.getPartAmount2();
            if (p1 != null && p1.compareTo(BigDecimal.ZERO) > 0)
                h.append("<tr><td class=\"l\">Part Payment 1</td><td class=\"a\">").append(currency).append(" ").append(df.format(p1)).append("</td></tr>");
            if (p2 != null && p2.compareTo(BigDecimal.ZERO) > 0)
                h.append("<tr><td class=\"l\">Part Payment 2</td><td class=\"a\">").append(currency).append(" ").append(df.format(p2)).append("</td></tr>");
        }
        h.append("<tr class=\"gr\"><td class=\"l\">Total Amount</td><td class=\"a\">").append(currency).append(" ").append(df.format(total)).append("</td></tr>")
         .append("</table></div>");

        // === PAYMENT ===
        h.append("<div class=\"pay\">")
         .append("<span>").append(paymentIcon(paymentMode)).append(" <strong>").append(esc(paymentMode)).append("</strong></span>")
         .append("<span><strong style=\"color:#0f172a\">").append(currency).append(" ").append(df.format(total)).append("</strong></span>")
         .append("</div>");

        // === REVIEW ===
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"rev\">")
             .append("\u2B50 How was your experience? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // === FOOTER ===
        h.append("<div class=\"ft\">")
         .append(esc(footer))
         .append("<div style=\"margin-top:6px\">Powered by <a href=\"https://play.google.com/store/apps/details?id=com.piquantservices.khanabooklite\" target=\"_blank\" style=\"color:#6366f1;font-weight:600;text-decoration:none\">KhanaBook</a></div>")
         .append("</div></div></body></html>");

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
