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
        String currency = (profile != null && profile.getCurrency() != null) ? profile.getCurrency() : "\u20B9";

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
        for (BillItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            String name = blank(item.getItemName());
            String variant = blank(item.getVariantName());
            String label;
            if (variant.isEmpty()) {
                label = esc(name);
            } else {
                label = esc(name) + "<br><span style=\"color:#666;font-size:0.85em\">" + esc(variant) + "</span>";
            }
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            String price = df.format(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
            String lineTotal = df.format(item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO);
            rows.append("<tr>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee\">").append(label).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:center\">").append(qty).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:right\">").append(currency).append(" ").append(price).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:right\">").append(currency).append(" ").append(lineTotal).append("</td>")
                .append("</tr>");
        }

        StringBuilder h = new StringBuilder(4096);
        h.append("<!DOCTYPE html><html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
          .append("<title>Invoice - ").append(esc(shopName)).append("</title><style>")
          .append("*{margin:0;padding:0;box-sizing:border-box}")
          .append("body{font-family:'Segoe UI',system-ui,-apple-system,sans-serif;background:#f5f5f5;color:#222;padding:20px}")
          .append(".page{max-width:740px;margin:0 auto;background:#fff;border-radius:12px;box-shadow:0 2px 16px rgba(0,0,0,.08);overflow:hidden}")
          .append(".header{display:flex;align-items:center;gap:16px;padding:28px 32px 20px;border-bottom:2px solid #f0f0f0}")
          .append(".header img{width:64px;height:64px;object-fit:contain;border-radius:8px}")
          .append(".header h1{font-size:1.3em;color:#1a1a1a}")
          .append(".header .sub{font-size:.85em;color:#666;margin-top:2px}")
          .append(".meta{display:flex;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:18px 32px;background:#fafafa;font-size:.88em;color:#444}")
          .append(".meta .col{line-height:1.6}.meta .col strong{color:#222}")
          .append(".badge{display:inline-block;padding:2px 10px;border-radius:4px;font-size:.78em;font-weight:600;text-transform:uppercase}")
          .append(".badge.paid{background:#e6f7e6;color:#1a7d1a}")
          .append(".badge.pending{background:#fff3cd;color:#856404}")
          .append(".badge.draft{background:#e2e3e5;color:#383d41}")
          .append(".badge.completed{background:#d4edda;color:#155724}")
          .append(".badge.cancelled{background:#f8d7da;color:#721c24}")
          .append(".customer{padding:16px 32px;border-bottom:1px solid #f0f0f0;font-size:.88em;color:#444}")
          .append("table{width:100%;border-collapse:collapse}")
          .append("thead th{padding:10px 6px;font-size:.78em;text-transform:uppercase;letter-spacing:.5px;color:#888;border-bottom:2px solid #e0e0e0;text-align:left}")
          .append("thead th.right{text-align:right}thead th.center{text-align:center}")
          .append("tbody td{vertical-align:top}")
          .append(".totals{padding:18px 32px;border-top:2px solid #f0f0f0}")
          .append(".totals table{width:auto;margin-left:auto}")
          .append(".totals td{padding:4px 0 4px 24px;font-size:.9em}")
          .append(".totals td.label{text-align:right;color:#555}")
          .append(".totals td.amount{text-align:right;font-weight:500;min-width:90px}")
          .append(".totals .grand td{padding-top:8px;font-size:1.1em;font-weight:700;border-top:2px solid #222}")
          .append(".payment{padding:16px 32px;border-top:1px solid #f0f0f0;font-size:.88em;color:#444;display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px}")
          .append(".footer{padding:20px 32px 28px;text-align:center;font-size:.82em;color:#888;border-top:1px solid #f0f0f0}")
          .append(".footer a{color:#2563eb;text-decoration:none}")
          .append(".footer .brand{margin-top:10px;font-size:.78em;color:#aaa}")
          .append("@media print{body{background:#fff;padding:0}.page{box-shadow:none;border-radius:0;max-width:100%}}")
          .append("</style></head><body><div class=\"page\">");

        // Header
        h.append("<div class=\"header\">");
        if (!logoUrl.isEmpty()) {
            h.append("<img src=\"").append(esc(logoUrl)).append("\" alt=\"").append(esc(shopName)).append("\" onerror=\"this.style.display='none'\">");
        }
        h.append("<div><h1>").append(esc(shopName)).append("</h1><div class=\"sub\">");
        if (!gstin.isEmpty()) h.append(" | GST: ").append(esc(gstin));
        if (!fssai.isEmpty()) h.append(" | FSSAI: ").append(esc(fssai));
        if (!address.isEmpty()) h.append("<br>").append(esc(address));
        h.append("</div></div></div>");

        // Meta
        h.append("<div class=\"meta\"><div class=\"col\">")
         .append("<strong>Invoice</strong> #").append(esc(orderCode)).append("<br>")
         .append("<strong>Date</strong> ").append(esc(date)).append("<br>")
         .append("<strong>Status</strong> <span class=\"badge ").append(os.toLowerCase()).append("\">").append(esc(orderStatus)).append("</span>");
        if (!phone.isEmpty() || !email.isEmpty()) {
            h.append("<br><small>").append(esc(phone));
            if (!phone.isEmpty() && !email.isEmpty()) h.append(" | ");
            h.append(esc(email)).append("</small>");
        }
        h.append("</div><div class=\"col\" style=\"text-align:right\">")
         .append("<strong>Payment</strong> ").append(esc(paymentMode)).append("<br>")
         .append("<strong>Status</strong> <span class=\"badge ").append(ps.toLowerCase()).append("\">").append(esc(paymentStatus)).append("</span>")
         .append("</div></div>");

        // Customer
        if (!customerName.isEmpty() || !customerPhone.isEmpty()) {
            h.append("<div class=\"customer\">");
            if (!customerName.isEmpty()) h.append("<strong>Customer:</strong> ").append(esc(customerName));
            if (!customerName.isEmpty() && !customerPhone.isEmpty()) h.append(" \u00B7 ");
            if (!customerPhone.isEmpty()) h.append(esc(customerPhone));
            h.append("</div>");
        }

        // Items table
        h.append("<table><thead><tr><th style=\"width:46%\">Item</th><th class=\"center\" style=\"width:10%\">Qty</th><th class=\"right\" style=\"width:20%\">Price</th><th class=\"right\" style=\"width:24%\">Total</th></tr></thead><tbody>")
         .append(rows)
         .append("</tbody></table>");

        // Totals
        h.append("<div class=\"totals\"><table>")
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
        h.append("<tr class=\"grand\"><td class=\"label\">Total</td><td class=\"amount\">").append(currency).append(" ").append(df.format(total)).append("</td></tr>")
         .append("</table></div>");

        // Payment
        h.append("<div class=\"payment\">")
         .append("<div><strong>Payment Method:</strong> ").append(esc(paymentMode)).append("</div>")
         .append("<div><strong>Order Status:</strong> ").append(esc(orderStatus)).append("</div>")
         .append("</div>");

        // Review
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"footer\" style=\"padding:12px 32px;border-top:1px solid #eee;font-size:.85em\">")
             .append("\u2605 Like our service? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // Footer
        h.append("<div class=\"footer\">")
         .append(esc(footer))
         .append("<div class=\"brand\">Powered by <strong>KhanaBook</strong></div></div>")
         .append("</div></body></html>");

        return h.toString();
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
