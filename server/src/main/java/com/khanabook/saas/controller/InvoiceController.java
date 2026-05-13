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
            String itemTax = "";
            if (hasGst) {
                BigDecimal itemTotalVal = item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO;
                BigDecimal taxAmt = itemTotalVal.multiply(gstPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                itemTax = df.format(taxAmt);
            }
            String cls = rowIdx % 2 == 0 ? "" : " class=\"e\"";
            rows.append("<tr").append(cls).append(">")
                .append("<td class=\"n\">").append(label).append("</td>")
                .append("<td class=\"q\">").append(qty).append("</td>")
                .append("<td class=\"p\">").append(currency).append(" ").append(price).append("</td>")
                .append("<td class=\"p\">").append(itemTax.isEmpty() ? "\u2014" : currency + " " + itemTax).append("</td>")
                .append("<td class=\"t\">").append(currency).append(" ").append(lineTotal).append("</td>")
                .append("</tr>");
            rowIdx++;
        }

        double taxPct = hasGst && gstPct.compareTo(BigDecimal.ZERO) > 0
                ? gstPct.doubleValue() : 0;
        String totalStr = currency + " " + df.format(total);
        String psLabel = paymentStatus.isEmpty() ? "" : paymentStatus;
        String psClass = ps.isEmpty() ? "" : ps.toLowerCase();

        StringBuilder h = new StringBuilder(8192);

        // === HEAD ===
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
         .append("<title>Invoice - ").append(esc(shopName)).append("</title>")
         .append("<style>");

        // === RESET + BASE ===
        h.append(
        "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;450;500;600;700&display=swap');"
        + "*,:after,:before{margin:0;padding:0;box-sizing:border-box}"
        + "body{"
        + "font-family:'Inter',-apple-system,system-ui,sans-serif;"
        + "background:#f1f5f9;color:#0f172a;padding:32px;min-height:100vh;"
        + "display:flex;align-items:flex-start;justify-content:center;"
        + "line-height:1.5;-webkit-font-smoothing:antialiased"
        + "}"
        + ".pg{"
        + "max-width:816px;width:100%;margin:0 auto;background:#fff;"
        + "border-radius:12px;box-shadow:0 1px 2px rgba(0,0,0,.04),0 8px 24px rgba(0,0,0,.06);"
        + "padding:48px 48px 32px"
        + "}");

        // === UTILITY ===
        h.append(
        ".row{display:flex;justify-content:space-between;flex-wrap:wrap;gap:24px}"
        + ".col{flex:1;min-width:220px}"
        + ".lbl{font-size:.7em;text-transform:uppercase;letter-spacing:.05em;color:#94a3b8;font-weight:600;margin-bottom:4px}"
        + ".val{color:#0f172a;font-size:.9em;line-height:1.6}"
        + ".val strong{font-weight:600}"
        + ".mt8{margin-top:8px}.mt16{margin-top:16px}.mt24{margin-top:24px}.mt32{margin-top:32px}"
        + ".mb8{margin-bottom:8px}.mb16{margin-bottom:16px}"
        + ".sep{height:1px;background:#e9edf2;margin:24px 0}"
        + ".sep-light{height:1px;background:#f1f4f9;margin:16px 0}"
        + ".txt-right{text-align:right}"
        + ".txt-center{text-align:center}"
        + ".txt-muted{color:#64748b;font-size:.85em}");

        // === BADGE ===
        h.append(
        ".bdg{display:inline-flex;align-items:center;gap:5px;padding:4px 12px;"
        + "border-radius:20px;font-size:.72em;font-weight:600;letter-spacing:.01em}"
        + ".bdg-dot{width:6px;height:6px;border-radius:50%;display:inline-block}"
        + ".bdg-paid{background:#ecfdf5;color:#065f46}"
        + ".bdg-paid .bdg-dot{background:#059669}"
        + ".bdg-pending{background:#fef3c7;color:#92400e}"
        + ".bdg-pending .bdg-dot{background:#d97706}"
        + ".bdg-completed{background:#ecfdf5;color:#065f46}"
        + ".bdg-completed .bdg-dot{background:#059669}"
        + ".bdg-success{background:#ecfdf5;color:#065f46}"
        + ".bdg-success .bdg-dot{background:#059669}"
        + ".bdg-cancelled{background:#fef2f2;color:#991b1b}"
        + ".bdg-cancelled .bdg-dot{background:#dc2626}"
        + ".bdg-failed{background:#fef2f2;color:#991b1b}"
        + ".bdg-failed .bdg-dot{background:#dc2626}"
        + ".bdg-draft,.bdg-refunded{background:#f1f5f9;color:#475569}"
        + ".bdg-draft .bdg-dot,.bdg-refunded .bdg-dot{background:#64748b}");

        // === HEADER ===
        h.append(
        ".hdr-top{display:flex;justify-content:space-between;align-items:flex-start;gap:24px}"
        + ".hdr-brand{display:flex;align-items:center;gap:16px}"
        + ".hdr-logo{width:48px;height:48px;border-radius:10px;object-fit:contain;"
        + "border:1px solid #e9edf2;background:#fff;padding:4px;flex-shrink:0}"
        + ".hdr-meta{text-align:right;flex-shrink:0}"
        + ".hdr-title{font-size:1.5em;font-weight:700;color:#0f172a;letter-spacing:-.03em}"
        + ".hdr-shop{font-size:1em;font-weight:600;color:#0f172a}"
        + ".hdr-detail{color:#64748b;font-size:.82em;line-height:1.6;margin-top:2px}"
        + ".hdr-line{display:flex;gap:6px;justify-content:flex-end;font-size:.8em;color:#64748b;margin-top:4px}");

        // === ADDRESS GRID ===
        h.append(
        ".addr{display:flex;gap:32px;flex-wrap:wrap}"
        + ".addr-card{flex:1;min-width:200px;background:#fafbfc;border:1px solid #e9edf2;"
        + "border-radius:10px;padding:16px 20px}"
        + ".addr-card .ttl{font-size:.68em;text-transform:uppercase;letter-spacing:.05em;"
        + "color:#94a3b8;font-weight:600;margin-bottom:8px}"
        + ".addr-card .ct{font-size:.85em;color:#0f172a;line-height:1.7}");

        // === TABLE ===
        h.append(
        ".tw{overflow-x:auto}"
        + "table{width:100%;border-collapse:collapse;font-size:.85em}"
        + "thead th{"
        + "padding:10px 8px;font-size:.65em;text-transform:uppercase;letter-spacing:.05em;"
        + "color:#94a3b8;font-weight:600;border-bottom:2px solid #e9edf2;text-align:left}"
        + "thead th.r{text-align:right}"
        + "thead th.c{text-align:center}"
        + "tbody tr{border-bottom:1px solid #f1f4f9}"
        + "tbody tr:last-child{border-bottom:none}"
        + "tbody td{padding:12px 8px;color:#1e293b;vertical-align:top;font-size:.87em}"
        + "tbody td.q{text-align:center;color:#64748b}"
        + "tbody td.p{text-align:right;color:#1e293b}"
        + "tbody td.t{text-align:right;font-weight:600;color:#0f172a}");

        // === SUMMARY CARD ===
        h.append(
        ".sum-wrap{display:flex;justify-content:flex-end;margin-top:16px}"
        + ".sum-card{"
        + "width:300px;background:#fafbfc;border:1px solid #e9edf2;"
        + "border-radius:10px;padding:20px 24px}"
        + ".sum-row{display:flex;justify-content:space-between;padding:5px 0;font-size:.84em}"
        + ".sum-row .l{color:#64748b}"
        + ".sum-row .r{color:#1e293b;font-weight:500;text-align:right}"
        + ".sum-div{height:1px;background:#e9edf2;margin:8px 0}"
        + ".sum-total{display:flex;justify-content:space-between;padding:8px 0 0;"
        + "font-size:1.05em;font-weight:700;color:#0f172a;border-top:2px solid #0f172a;margin-top:4px}");

        // === PAYMENT ROW ===
        h.append(
        ".pay-row{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;"
        + "padding:8px 0;font-size:.85em}");

        // === NOTES ===
        h.append(
        ".notes{background:#fafbfc;border:1px solid #e9edf2;border-radius:10px;"
        + "padding:16px 20px;font-size:.82em;color:#64748b;line-height:1.7}"
        + ".notes strong{color:#0f172a}");

        // === FOOTER ===
        h.append(
        ".ft{padding:16px 0 0;text-align:center;font-size:.78em;color:#94a3b8;border-top:1px solid #e9edf2;margin-top:24px}"
        + ".ft a{color:#6366f1;font-weight:600;text-decoration:none}"
        + ".ft a:hover{text-decoration:underline}");

        // === REVIEW ===
        h.append(
        ".rev{text-align:center;font-size:.82em;color:#64748b;padding:8px 0 0}"
        + ".rev a{color:#2563eb;font-weight:600;text-decoration:none}");

        // === PRINT ===
        h.append(
        "@media print{"
        + "@page{margin:0}"
        + "body{background:#fff!important;padding:0!important;display:block}"
        + ".pg{box-shadow:none!important;border-radius:0!important;max-width:100%!important;padding:32px 40px}"
        + ".bdg-paid,.bdg-pending,.bdg-completed,.bdg-success,.bdg-cancelled,.bdg-failed,.bdg-draft,.bdg-refunded,.addr-card,.sum-card,.notes"
        + "{-webkit-print-color-adjust:exact;print-color-adjust:exact}"
        + ".sep,.sep-light{background:#000!important}"
        + "}");

        // === MOBILE ===
        h.append(
        "@media(max-width:640px){"
        + "body{padding:12px}"
        + ".pg{padding:20px 16px 24px;border-radius:8px}"
        + ".hdr-top{flex-direction:column;align-items:center;text-align:center}"
        + ".hdr-meta{text-align:center;width:100%}"
        + ".hdr-line{justify-content:center}"
        + ".addr{gap:16px}"
        + ".addr-card{padding:14px 16px}"
        + ".sum-card{width:100%}"
        + "tbody td{padding:10px 6px;font-size:.82em}"
        + "thead th{font-size:.6em;padding:8px 6px}"
        + ".pay-row{flex-direction:column;align-items:flex-start;gap:6px}"
        + ".notes{padding:14px 16px}"
        + "}"
        + "</style></head><body><div class=\"pg\">");

        // ════════════════════════════════════════════
        // HEADER: Logo + Brand left, Invoice meta right
        // ════════════════════════════════════════════
        h.append("<div class=\"hdr-top\">")
         .append("<div class=\"hdr-brand\">");
        if (!logoUrl.isEmpty()) {
            h.append("<img class=\"hdr-logo\" src=\"").append(esc(logoUrl))
             .append("\" alt=\"").append(esc(shopName)).append("\" onerror=\"this.style.display='none'\">");
        }
        h.append("<div><div class=\"hdr-shop\">").append(esc(shopName)).append("</div>")
         .append("<div class=\"hdr-detail\">");
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
        h.append("</div></div></div>")
         .append("<div class=\"hdr-meta\">")
         .append("<div class=\"hdr-title\">INVOICE</div>")
         .append("<div class=\"hdr-line\"><span style=\"color:#94a3b8\">#</span>").append(esc(orderCode)).append("</div>")
         .append("<div class=\"hdr-line\"><span style=\"color:#94a3b8\">Issued:</span>").append(esc(date)).append("</div>")
         .append("<div class=\"hdr-line\" style=\"margin-top:6px\">")
         .append("<span class=\"bdg bdg-").append(psClass).append("\">")
         .append("<span class=\"bdg-dot\"></span>").append(esc(psLabel.isEmpty() ? "Completed" : psLabel))
         .append("</span></div>")
         .append("</div></div>");

        // ════════════════════════════════════════════
        // ADDRESSES: From / Bill To
        // ════════════════════════════════════════════
        h.append("<div class=\"addr mt24\">")
         .append("<div class=\"addr-card\">")
         .append("<div class=\"ttl\">From</div>")
         .append("<div class=\"ct\"><strong>").append(esc(shopName)).append("</strong><br>");
        if (!address.isEmpty()) h.append(esc(address)).append("<br>");
        if (!gstin.isEmpty()) h.append("GST: ").append(esc(gstin)).append("<br>");
        if (!fssai.isEmpty()) h.append("FSSAI: ").append(esc(fssai)).append("<br>");
        if (!phone.isEmpty() || !email.isEmpty()) {
            if (!phone.isEmpty()) h.append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" | ");
            if (!email.isEmpty()) h.append(esc(email));
        }
        h.append("</div></div>")
         .append("<div class=\"addr-card\">")
         .append("<div class=\"ttl\">Bill To</div>")
         .append("<div class=\"ct\">");
        if (!customerName.isEmpty()) {
            h.append("<strong>").append(esc(customerName)).append("</strong><br>");
        } else {
            h.append("<strong>Guest</strong><br>");
        }
        if (!customerPhone.isEmpty()) h.append(esc(customerPhone)).append("<br>");
        h.append("</div></div></div>");

        // ════════════════════════════════════════════
        // PAYMENT ROW
        // ════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>")
         .append("<div class=\"pay-row\">")
         .append("<div>").append(paymentIcon(paymentMode)).append(" <strong>").append(esc(paymentMode)).append("</strong></div>")
         .append("<div style=\"color:#94a3b8;font-size:.8em\">Invoice #INV").append(bill.getLifetimeOrderId()).append(" &middot; ").append(esc(date)).append("</div>")
         .append("</div>");

        // ════════════════════════════════════════════
        // ITEMS TABLE
        // ════════════════════════════════════════════
        h.append("<div class=\"sep\"></div>")
         .append("<div class=\"tw\"><table><thead><tr>")
         .append("<th style=\"width:44%\">Description</th><th class=\"c\" style=\"width:8%\">Qty</th>")
         .append("<th class=\"r\" style=\"width:18%\">Rate</th><th class=\"r\" style=\"width:18%\">Tax</th><th class=\"r\" style=\"width:18%\">Amount</th>")
         .append("</tr></thead><tbody>").append(rows)
         .append("</tbody></table></div>");

        // ════════════════════════════════════════════
        // SUMMARY CARD
        // ════════════════════════════════════════════
        h.append("<div class=\"sum-wrap\">")
         .append("<div class=\"sum-card\">")
         .append("<div class=\"sum-row\"><span class=\"l\">Subtotal</span><span class=\"r\">").append(currency).append(" ").append(df.format(sub)).append("</span></div>");
        if (hasGst) {
            String half = df.format(gstPct.divide(BigDecimal.valueOf(2)));
            h.append("<div class=\"sum-row\"><span class=\"l\">CGST @").append(half).append("%</span><span class=\"r\">").append(currency).append(" ").append(df.format(cgst)).append("</span></div>")
             .append("<div class=\"sum-row\"><span class=\"l\">SGST @").append(half).append("%</span><span class=\"r\">").append(currency).append(" ").append(df.format(sgst)).append("</span></div>");
        }
        if (hasCustomTax) {
            String label = customTaxName.isEmpty() ? "Custom Tax" : esc(customTaxName);
            h.append("<div class=\"sum-row\"><span class=\"l\">").append(label).append("</span><span class=\"r\">").append(currency).append(" ").append(df.format(customTax)).append("</span></div>");
        }
        if (hasPartPayments) {
            BigDecimal p1 = bill.getPartAmount1();
            BigDecimal p2 = bill.getPartAmount2();
            if (p1 != null && p1.compareTo(BigDecimal.ZERO) > 0)
                h.append("<div class=\"sum-row\"><span class=\"l\">Part Payment 1</span><span class=\"r\">").append(currency).append(" ").append(df.format(p1)).append("</span></div>");
            if (p2 != null && p2.compareTo(BigDecimal.ZERO) > 0)
                h.append("<div class=\"sum-row\"><span class=\"l\">Part Payment 2</span><span class=\"r\">").append(currency).append(" ").append(df.format(p2)).append("</span></div>");
        }
        h.append("<div class=\"sum-div\"></div>")
         .append("<div class=\"sum-total\"><span>Total</span><span>").append(totalStr).append("</span></div>")
         .append("</div></div>");

        // ════════════════════════════════════════════
        // NOTES
        // ════════════════════════════════════════════
        h.append("<div class=\"mt24\"><div class=\"notes\">")
         .append("<strong>Notes / Terms</strong><br>")
         .append(esc(footer.isEmpty() ? "Thank you for your business!" : footer))
         .append("</div></div>");

        // ════════════════════════════════════════════
        // REVIEW CTA
        // ════════════════════════════════════════════
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"rev mt16\">")
             .append("\u2B50 How was your experience? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // ════════════════════════════════════════════
        // FOOTER
        // ════════════════════════════════════════════
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
