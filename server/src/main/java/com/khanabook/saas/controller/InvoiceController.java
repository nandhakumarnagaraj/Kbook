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

        String orderCode = (bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank())
                ? bill.getDailyOrderDisplay() : "INV" + bill.getLifetimeOrderId();
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
                label = esc(name) + "<br><span style=\"color:#64748b;font-size:0.82em\">" + esc(variant) + "</span>";
            }
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            String price = df.format(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
            String lineTotal = df.format(item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO);
            String bg = rowIdx % 2 == 0 ? "" : " style=\"background:#f8fafc\"";
            rows.append("<tr").append(bg).append(">")
                .append("<td style=\"padding:10px 8px\">").append(label).append("</td>")
                .append("<td style=\"padding:10px 8px;text-align:center;color:#475569\">").append(qty).append("</td>")
                .append("<td style=\"padding:10px 8px;text-align:right;color:#475569\">").append(price).append("</td>")
                .append("<td style=\"padding:10px 8px;text-align:right;font-weight:600;color:#1e293b\">").append(lineTotal).append("</td>")
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
        +   "font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif;"
        +   "background:linear-gradient(135deg,#f1f5f9 0%,#e2e8f0 100%);"
        +   "color:#1e293b;padding:24px;min-height:100vh;display:flex;align-items:center;justify-content:center"
        + "}"
        + ".page{"
        +   "max-width:800px;width:100%;margin:0 auto;background:#ffffff;"
        +   "border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,.06),0 1px 4px rgba(0,0,0,.04);"
        +   "overflow:hidden;position:relative"
        + "}");

        // Brand watermark
        h.append(
        ".page::before{"
        +   "content:'';position:absolute;top:-120px;right:-120px;width:300px;height:300px;"
        +   "background:radial-gradient(circle,rgba(99,102,241,.04) 0%,transparent 70%);"
        +   "border-radius:50%;pointer-events:none;z-index:0"
        + "}");

        // Header
        h.append(
        ".header-wrap{"
        +   "background:linear-gradient(135deg,#1e293b 0%,#334155 100%);"
        +   "padding:32px 36px 28px;text-align:center;position:relative;z-index:1"
        + "}"
        + ".header-wrap .logo{"
        +   "width:100px;height:100px;border-radius:12px;object-fit:contain;"
        +   "border:2px solid rgba(255,255,255,.15);background:#fff;padding:6px;"
        +   "margin-bottom:12px"
        + "}"
        + ".header-wrap h1{color:#fff;font-size:1.35em;font-weight:700;letter-spacing:-.3px}"
        + ".header-wrap .sub{color:#94a3b8;font-size:.82em;margin-top:4px;line-height:1.5;max-width:500px;margin-left:auto;margin-right:auto}"
        + ".header-wrap .sub span{color:#cbd5e1}");

        // Invoice ribbon
        h.append(
        ".ribbon{"
        +   "background:#fff;padding:18px 36px;display:flex;justify-content:space-between;"
        +   "flex-wrap:wrap;gap:12px;border-bottom:1px solid #e2e8f0;position:relative;z-index:1"
        + "}"
        + ".ribbon .item{font-size:.82em;color:#64748b}"
        + ".ribbon .item strong{color:#1e293b;font-weight:600;display:block;font-size:1.05em;margin-top:2px}");

        // Customer
        h.append(
        ".customer-wrap{"
        +   "padding:16px 36px;border-bottom:1px solid #e2e8f0;font-size:.88em;"
        +   "display:flex;align-items:center;gap:8px;flex-wrap:wrap;position:relative;z-index:1"
        + "}"
        + ".customer-wrap .label{color:#64748b;font-weight:500}"
        + ".customer-wrap .val{color:#1e293b}");

        // Table
        h.append(
        ".table-wrap{padding:4px 28px 0;position:relative;z-index:1}"
        + "table{width:100%;border-collapse:collapse}"
        + "thead th{"
        +   "padding:12px 8px;font-size:.72em;text-transform:uppercase;letter-spacing:.8px;"
        +   "color:#64748b;font-weight:600;border-bottom:2px solid #e2e8f0;text-align:left"
        + "}"
        + "thead th.right{text-align:right}"
        + "thead th.center{text-align:center}"
        + "tbody tr{border-bottom:1px solid #f1f5f9}"
        + "tbody tr:last-child{border-bottom:none}"
        + "tbody td{font-size:.9em;color:#334155;vertical-align:top}");

        // Totals
        h.append(
        ".totals-wrap{"
        +   "margin:0 28px 0;padding:16px 8px 8px;border-top:2px solid #e2e8f0;"
        +   "position:relative;z-index:1"
        + "}"
        + ".totals-wrap table{width:auto;margin-left:auto}"
        + ".totals-wrap td{padding:5px 0 5px 32px;font-size:.88em}"
        + ".totals-wrap td.label{text-align:right;color:#64748b}"
        + ".totals-wrap td.amount{text-align:right;font-weight:500;min-width:100px;color:#334155}"
        + ".totals-wrap .grand td{"
        +   "padding-top:10px;font-size:1.15em;font-weight:700;color:#1e293b;"
        +   "border-top:2px solid #1e293b"
        + "}");

        // Payment
        h.append(
        ".payment-wrap{"
        +   "margin:8px 28px 0;padding:14px 8px;border-top:1px solid #e2e8f0;"
        +   "display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;"
        +   "font-size:.85em;color:#64748b;position:relative;z-index:1"
        + "}"
        + ".payment-wrap strong{color:#334155;font-weight:600}");

        // Review CTA
        h.append(
        ".review-wrap{"
        +   "margin:0 28px;padding:16px 8px;text-align:center;"
        +   "background:linear-gradient(135deg,#fef9c3 0%,#fef3c7 100%);"
        +   "border-radius:10px;font-size:.85em;color:#92400e;position:relative;z-index:1"
        + "}"
        + ".review-wrap a{color:#1d4ed8;font-weight:600;text-decoration:none}"
        + ".review-wrap a:hover{text-decoration:underline}");

        // Footer
        h.append(
        ".footer-wrap{"
        +   "margin-top:20px;padding:20px 36px 24px;text-align:center;font-size:.8em;"
        +   "color:#94a3b8;border-top:1px solid #e2e8f0;position:relative;z-index:1"
        + "}"
        + ".footer-wrap .brand{margin-top:8px;font-size:.78em;color:#94a3b8}"
        + ".footer-wrap .brand strong{color:#6366f1}");

        // Print
        h.append(
        "@media print{"
        +   "body{background:#fff!important;padding:0;display:block;min-height:auto}"
        +   ".page{box-shadow:none!important;border-radius:0!important;max-width:100%!important}"
        +   ".header-wrap{background:#1e293b!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        +   ".review-wrap,.badge-completed,.badge-cancelled,.badge-draft,.badge-paid,.badge-success,.badge-pending,.badge-failed,.badge-refunded"
        +   "{-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        +   "tbody tr:nth-child(even){background:#f8fafc!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        + "}"
        + "@media(max-width:600px){"
        +   "body{padding:12px}"
        +   ".header-wrap{padding:20px 16px;flex-direction:column;text-align:center}"
        +   ".ribbon,.customer-wrap,.table-wrap,.totals-wrap,.payment-wrap,.review-wrap{padding-left:16px;padding-right:16px}"
        +   ".totals-wrap table{width:100%}"
        +   ".totals-wrap td{padding-left:12px}"
        + "}"
        + "</style></head><body><div class=\"page\">");

        // === HEADER ===
        h.append("<div class=\"header-wrap\">");
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
            h.append("<span>");
            if (!phone.isEmpty()) h.append("\u260E ").append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" &nbsp;|&nbsp; ");
            if (!email.isEmpty()) h.append("\u2709 ").append(esc(email));
            h.append("</span>");
        }
        h.append("</div></div>");

        // === RIBBON ===
        h.append("<div class=\"ribbon\">")
         .append("<div class=\"item\">Invoice #<strong>").append(esc(orderCode)).append("</strong></div>")
         .append("<div class=\"item\">Date<strong>").append(esc(date)).append("</strong></div>")
         .append("</div>");

        // === CUSTOMER ===
        if (!customerName.isEmpty() || !customerPhone.isEmpty()) {
            h.append("<div class=\"customer-wrap\">")
             .append("<span class=\"label\">\uD83D\uDC64 Bill to:</span>");
            if (!customerName.isEmpty()) h.append("<span class=\"val\">").append(esc(customerName)).append("</span>");
            if (!customerName.isEmpty() && !customerPhone.isEmpty()) h.append("<span style=\"color:#cbd5e1\">|</span>");
            if (!customerPhone.isEmpty()) h.append("<span class=\"val\">").append(esc(customerPhone)).append("</span>");
            h.append("</div>");
        }

        // === TABLE ===
        h.append("<div class=\"table-wrap\"><table><thead><tr>")
         .append("<th style=\"width:44%\">Item</th><th class=\"center\" style=\"width:10%\">Qty</th>")
         .append("<th class=\"right\" style=\"width:20%\">Price</th><th class=\"right\" style=\"width:26%\">Total</th>")
         .append("</tr></thead><tbody>").append(rows)
         .append("</tbody></table></div>");

        // === TOTALS ===
        h.append("<div class=\"totals-wrap\"><table>")
         .append("<tr><td class=\"label\">Subtotal</td><td class=\"amount\">").append(currency).append(" ").append(df.format(sub)).append("</td></tr>");
        if (hasGst) {
            String half = df.format(gstPct.divide(BigDecimal.valueOf(2)));
            h.append("<tr><td class=\"label\">CGST @").append(half).append("%</td><td class=\"amount\">").append(currency).append(" ").append(df.format(cgst)).append("</td></tr>")
             .append("<tr><td class=\"label\">SGST @").append(half).append("%</td><td class=\"amount\">").append(currency).append(" ").append(df.format(sgst)).append("</td></tr>");
        }
        if (hasCustomTax) {
            String label = customTaxName.isEmpty() ? "Custom Tax" : esc(customTaxName);
            h.append("<tr><td class=\"label\">").append(label).append("</td><td class=\"amount\">").append(currency).append(" ").append(df.format(customTax)).append("</td></tr>");
        }
        if (hasPartPayments) {
            BigDecimal p1 = bill.getPartAmount1();
            BigDecimal p2 = bill.getPartAmount2();
            if (p1 != null && p1.compareTo(BigDecimal.ZERO) > 0)
                h.append("<tr><td class=\"label\">Part Payment 1</td><td class=\"amount\">").append(currency).append(" ").append(df.format(p1)).append("</td></tr>");
            if (p2 != null && p2.compareTo(BigDecimal.ZERO) > 0)
                h.append("<tr><td class=\"label\">Part Payment 2</td><td class=\"amount\">").append(currency).append(" ").append(df.format(p2)).append("</td></tr>");
        }
        h.append("<tr class=\"grand\"><td class=\"label\">Total Amount</td><td class=\"amount\">").append(currency).append(" ").append(df.format(total)).append("</td></tr>")
         .append("</table></div>");

        // === PAYMENT ===
        h.append("<div class=\"payment-wrap\">")
         .append("<div><strong>Payment Method:</strong> ").append(paymentIcon(paymentMode)).append(" ").append(esc(paymentMode)).append("</div>")
         .append("</div>");

        // === REVIEW ===
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"review-wrap\">")
             .append("\u2B50 How was your experience? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // === FOOTER ===
        h.append("<div class=\"footer-wrap\">")
         .append(esc(footer))
         .append("<div class=\"brand\">Powered by <a href=\"https://play.google.com/store/apps/details?id=com.piquantservices.khanabooklite\" target=\"_blank\" style=\"color:#6366f1;font-weight:700;text-decoration:none\">KhanaBook</a></div>")
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
