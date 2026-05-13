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
import java.util.UUID;

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
            @PathVariable UUID token) {

        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null || !bill.getRestaurantId().equals(restaurantId)
                || !token.equals(bill.getPublicToken())
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

        String shopName = profile != null ? profile.getShopName() : "Shop";
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
        String paymentStatus = bill.getPaymentStatus() != null
                ? bill.getPaymentStatus().substring(0, 1).toUpperCase() + bill.getPaymentStatus().substring(1) : "";
        String orderStatus = bill.getOrderStatus() != null
                ? bill.getOrderStatus().substring(0, 1).toUpperCase() + bill.getOrderStatus().substring(1) : "";

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
            String label = variant.isEmpty() ? esc(name) : esc(name) + " <span style=\"color:#666;font-size:0.85em\">(" + esc(variant) + ")</span>";
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            String price = df.format(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
            String lineTotal = df.format(item.getItemTotal() != null ? item.getItemTotal() : BigDecimal.ZERO);
            rows.append("<tr>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee\">").append(label).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:center\">").append(qty).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:right\">").append(currency).append(" ").append(price).append("</td>")
                .append("<td style=\"padding:8px 6px;border-bottom:1px solid #eee;text-align:right\">").append(currency).append(" ").append(lineTotal).append("</td>")
                .append("</tr>\n");
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Invoice - %s</title>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family:'Segoe UI',system-ui,-apple-system,sans-serif; background:#f5f5f5; color:#222; padding:20px; }
  .page { max-width:740px; margin:0 auto; background:#fff; border-radius:12px; box-shadow:0 2px 16px rgba(0,0,0,.08); overflow:hidden; }
  .header { display:flex; align-items:center; gap:16px; padding:28px 32px 20px; border-bottom:2px solid #f0f0f0; }
  .header img { width:64px; height:64px; object-fit:contain; border-radius:8px; }
  .header h1 { font-size:1.3em; color:#1a1a1a; }
  .header .sub { font-size:0.85em; color:#666; margin-top:2px; }
  .meta { display:flex; justify-content:space-between; flex-wrap:wrap; gap:12px; padding:18px 32px; background:#fafafa; font-size:0.88em; color:#444; }
  .meta .col { line-height:1.6; }
  .meta .col strong { color:#222; }
  .badge { display:inline-block; padding:2px 10px; border-radius:4px; font-size:0.78em; font-weight:600; text-transform:uppercase; }
  .badge.paid { background:#e6f7e6; color:#1a7d1a; }
  .badge.pending { background:#fff3cd; color:#856404; }
  .badge.draft { background:#e2e3e5; color:#383d41; }
  .badge.completed { background:#d4edda; color:#155724; }
  .badge.cancelled { background:#f8d7da; color:#721c24; }
  .customer { padding:16px 32px; border-bottom:1px solid #f0f0f0; font-size:0.88em; color:#444; }
  .customer strong { color:#222; }
  table { width:100%; border-collapse:collapse; }
  thead th { padding:10px 6px; font-size:0.78em; text-transform:uppercase; letter-spacing:.5px; color:#888; border-bottom:2px solid #e0e0e0; text-align:left; }
  thead th.right { text-align:right; }
  thead th.center { text-align:center; }
  tbody td { vertical-align:top; }
  .totals { padding:18px 32px; border-top:2px solid #f0f0f0; }
  .totals table { width:auto; margin-left:auto; }
  .totals td { padding:4px 0 4px 24px; font-size:0.9em; }
  .totals td.label { text-align:right; color:#555; }
  .totals td.amount { text-align:right; font-weight:500; min-width:90px; }
  .totals .grand td { padding-top:8px; font-size:1.1em; font-weight:700; border-top:2px solid #222; }
  .payment { padding:16px 32px; border-top:1px solid #f0f0f0; font-size:0.88em; color:#444; display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; }
  .payment strong { color:#222; }
  .footer { padding:20px 32px 28px; text-align:center; font-size:0.82em; color:#888; border-top:1px solid #f0f0f0; }
  .footer a { color:#2563eb; text-decoration:none; }
  .footer a:hover { text-decoration:underline; }
  .footer .brand { margin-top:10px; font-size:0.78em; color:#aaa; }
  .empty { padding:40px 32px; text-align:center; color:#999; }
  @media print {
    body { background:#fff; padding:0; }
    .page { box-shadow:none; border-radius:0; max-width:100%; }
    .no-print { display:none !important; }
  }
</style>
</head>
<body>

<div class="page">

  <div class="header">
    %s
    <div>
      <h1>%s</h1>
      <div class="sub">%s%s%s</div>
    </div>
  </div>

  <div class="meta">
    <div class="col">
      <strong>Invoice</strong> #%s<br>
      <strong>Date</strong> %s<br>
      <strong>Status</strong> <span class="badge %s">%s</span>
      %s
    </div>
    <div class="col" style="text-align:right">
      <strong>Payment</strong> %s<br>
      <strong>Status</strong> <span class="badge %s">%s</span>
    </div>
  </div>

  %s

  <table>
    <thead>
      <tr>
        <th style="width:46%%">Item</th>
        <th class="center" style="width:10%%">Qty</th>
        <th class="right" style="width:20%%">Price</th>
        <th class="right" style="width:24%%">Total</th>
      </tr>
    </thead>
    <tbody>
      %s
    </tbody>
  </table>

  %s

  <div class="payment">
    <div><strong>Payment Method:</strong> %s</div>
    <div><strong>Order Status:</strong> %s</div>
  </div>

  %s

  <div class="footer">
    %s
    <div class="brand">Powered by <strong>KhanaBook</strong></div>
  </div>

</div>

</body>
</html>""".formatted(
                esc(shopName),
                logoBlock(logoUrl, shopName),
                esc(shopName),
                gstin.isEmpty() ? "" : " | GST: " + gstin,
                fssai.isEmpty() ? "" : " | FSSAI: " + fssai,
                address.isEmpty() ? "" : "<br>" + esc(address),
                esc(orderCode),
                date,
                orderStatus.toLowerCase(), esc(orderStatus),
                (phone.isEmpty() && email.isEmpty()) ? "" : "<br><small>" + phone + (phone.isEmpty() || email.isEmpty() ? "" : " | ") + email + "</small>",
                esc(paymentMode),
                paymentStatus.toLowerCase(), esc(paymentStatus),
                customerBlock(customerName, customerPhone),
                rows.toString(),
                totalsBlock(df, currency, sub, gstPct, cgst, sgst, hasGst, customTaxName, customTax, hasCustomTax, total, hasPartPayments, bill),
                reviewBlock(reviewUrl),
                esc(paymentMode),
                esc(orderStatus),
                esc(footer)
        );
    }

    private String logoBlock(String logoUrl, String shopName) {
        if (logoUrl.isEmpty()) return "";
        return "<img src=\"" + esc(logoUrl) + "\" alt=\"" + esc(shopName) + "\" onerror=\"this.style.display='none'\">";
    }

    private String customerBlock(String name, String phone) {
        if (name.isEmpty() && phone.isEmpty()) return "";
        String n = name.isEmpty() ? "" : "<strong>Customer:</strong> " + esc(name);
        String p = phone.isEmpty() ? "" : (n.isEmpty() ? "" : " &middot; ") + esc(phone);
        return "<div class=\"customer\">" + n + p + "</div>";
    }

    private String totalsBlock(DecimalFormat df, String currency, BigDecimal sub, BigDecimal gstPct,
                                BigDecimal cgst, BigDecimal sgst, boolean hasGst,
                                String customTaxName, BigDecimal customTax, boolean hasCustomTax,
                                BigDecimal total, boolean hasPartPayments, Bill bill) {
        StringBuilder sb = new StringBuilder("<div class=\"totals\"><table>");
        sb.append("<tr><td class=\"label\">Subtotal</td><td class=\"amount\">").append(currency).append(" ").append(df.format(sub)).append("</td></tr>");
        if (hasGst) {
            sb.append("<tr><td class=\"label\">CGST @").append(df.format(gstPct.divide(BigDecimal.valueOf(2)))).append("%</td><td class=\"amount\">").append(currency).append(" ").append(df.format(cgst)).append("</td></tr>");
            sb.append("<tr><td class=\"label\">SGST @").append(df.format(gstPct.divide(BigDecimal.valueOf(2)))).append("%</td><td class=\"amount\">").append(currency).append(" ").append(df.format(sgst)).append("</td></tr>");
        }
        if (hasCustomTax) {
            String label = customTaxName.isEmpty() ? "Custom Tax" : esc(customTaxName);
            sb.append("<tr><td class=\"label\">").append(label).append("</td><td class=\"amount\">").append(currency).append(" ").append(df.format(customTax)).append("</td></tr>");
        }
        if (hasPartPayments) {
            if (bill.getPartAmount1() != null && bill.getPartAmount1().compareTo(BigDecimal.ZERO) > 0) {
                sb.append("<tr><td class=\"label\">Part Payment 1</td><td class=\"amount\">").append(currency).append(" ").append(df.format(bill.getPartAmount1())).append("</td></tr>");
            }
            if (bill.getPartAmount2() != null && bill.getPartAmount2().compareTo(BigDecimal.ZERO) > 0) {
                sb.append("<tr><td class=\"label\">Part Payment 2</td><td class=\"amount\">").append(currency).append(" ").append(df.format(bill.getPartAmount2())).append("</td></tr>");
            }
        }
        sb.append("<tr class=\"grand\"><td class=\"label\">Total</td><td class=\"amount\">").append(currency).append(" ").append(df.format(total)).append("</td></tr>");
        sb.append("</table></div>");
        return sb.toString();
    }

    private String reviewBlock(String reviewUrl) {
        if (reviewUrl.isEmpty()) return "";
        return "<div class=\"footer\" style=\"padding:12px 32px;border-top:1px solid #eee;font-size:0.85em\">\u2605 Like our service? <a href=\"" + esc(reviewUrl) + "\" target=\"_blank\">Leave a review</a></div>";
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
