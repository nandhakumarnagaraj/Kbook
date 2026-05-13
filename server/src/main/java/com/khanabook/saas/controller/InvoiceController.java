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
                .append("<td class=\"n\">").append(label).append("</td>")
                .append("<td class=\"q\">").append(qty).append("</td>")
                .append("<td class=\"p\">").append(currency).append(" ").append(price).append("</td>")
                .append("<td class=\"p\">").append(itemTax.isEmpty() ? "\u2014" : currency + " " + itemTax).append("</td>")
                .append("<td class=\"t\">").append(currency).append(" ").append(lineTotal).append("</td>")
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

        // ═══════════════════════════════════════════════════════
        // FONT + RESET
        // ═══════════════════════════════════════════════════════
        h.append(
        "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');"
        + "@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&display=swap');"
        + "*,:after,:before{margin:0;padding:0;box-sizing:border-box}"
        + "body{"
        + "font-family:'Inter',system-ui,-apple-system,sans-serif;"
        + "background:#f1f5f9;color:#0f172a;padding:32px;min-height:100vh;"
        + "display:flex;align-items:flex-start;justify-content:center;"
        + "line-height:1.5;-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale"
        + "}");

        // ═══════════════════════════════════════════════════════
        // PAGE CONTAINER
        // ═══════════════════════════════════════════════════════
        h.append(
        ".pg{"
        + "max-width:816px;width:100%;margin:0 auto;background:#fff;position:relative;overflow:hidden;"
        + "border-radius:14px;box-shadow:0 1px 3px rgba(0,0,0,.04),0 8px 32px rgba(0,0,0,.08);"
        + "padding:48px 48px 24px"
        + "}");

        // ═══════════════════════════════════════════════════════
        // WATERMARK
        // ═══════════════════════════════════════════════════════
        if (showWatermark) {
            h.append(
            ".wm{"
            + "position:absolute;top:50%;left:50%;transform:translate(-50%,-50%)rotate(-30deg);"
            + "font-size:140px;font-weight:900;font-family:'Plus Jakarta Sans',sans-serif;"
            + "color:rgba(5,150,105,.045);pointer-events:none;z-index:0;user-select:none;white-space:nowrap;"
            + "letter-spacing:.15em"
            + "}");
        }

        // ═══════════════════════════════════════════════════════
        // UTILITY
        // ═══════════════════════════════════════════════════════
        h.append(
        ".mt4{margin-top:4px}.mt8{margin-top:8px}.mt12{margin-top:12px}.mt16{margin-top:16px}.mt20{margin-top:20px}.mt24{margin-top:24px}.mt32{margin-top:32px}"
        + ".mb4{margin-bottom:4px}.mb8{margin-bottom:8px}.mb12{margin-bottom:12px}.mb16{margin-bottom:16px}"
        + ".sep{height:1px;background:#e9edf2;margin:20px 0}"
        + ".sep-lg{height:1px;background:#e2e8f0;margin:28px 0}"
        + ".fw500{font-weight:500}.fw600{font-weight:600}.fw700{font-weight:700}"
        + ".text-muted{color:#64748b}.text-xs{font-size:.78em}"
        + ".flex{display:flex}.flex-col{flex-direction:column}.items-center{align-items:center}.justify-between{justify-content:space-between}.gap4{gap:4px}.gap8{gap:8px}.gap12{gap:12px}.gap16{gap:16px}.gap24{gap:24px}"
        + ".grid-2{display:grid;grid-template-columns:1fr 1fr;gap:24px}"
        + ".w-full{width:100%}");

        // ═══════════════════════════════════════════════════════
        // STATUS BADGE
        // ═══════════════════════════════════════════════════════
        h.append(
        ".bdg{display:inline-flex;align-items:center;gap:6px;padding:5px 14px;"
        + "border-radius:20px;font-size:.72em;font-weight:600;letter-spacing:.01em;line-height:1}"
        + ".bdg-dot{width:7px;height:7px;border-radius:50%;display:inline-block;flex-shrink:0}"
        + ".bdg-paid,.bdg-completed,.bdg-success{background:#ecfdf5;color:#065f46}"
        + ".bdg-paid .bdg-dot,.bdg-completed .bdg-dot,.bdg-success .bdg-dot{background:#059669;box-shadow:inset 0 0 0 1px rgba(5,150,105,.2)}"
        + ".bdg-pending,.bdg-unpaid,.bdg-partial,.bdg-partially_paid{background:#fffbeb;color:#92400e}"
        + ".bdg-pending .bdg-dot,.bdg-unpaid .bdg-dot,.bdg-partial .bdg-dot,.bdg-partially_paid .bdg-dot{background:#d97706;box-shadow:inset 0 0 0 1px rgba(217,119,6,.2)}"
        + ".bdg-cancelled,.bdg-failed,.bdg-overdue{background:#fef2f2;color:#991b1b}"
        + ".bdg-cancelled .bdg-dot,.bdg-failed .bdg-dot,.bdg-overdue .bdg-dot{background:#dc2626;box-shadow:inset 0 0 0 1px rgba(220,38,38,.2)}"
        + ".bdg-draft,.bdg-refunded{background:#f1f5f9;color:#475569}"
        + ".bdg-draft .bdg-dot,.bdg-refunded .bdg-dot{background:#64748b;box-shadow:inset 0 0 0 1px rgba(100,116,139,.2)}");

        // ═══════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════
        h.append(
        ".hdr{display:flex;justify-content:space-between;align-items:flex-start;gap:24px;position:relative;z-index:1}"
        + ".hdr-l{display:flex;align-items:center;gap:16px;min-width:0}"
        + ".hdr-logo{width:100px;height:100px;border-radius:16px;object-fit:contain;"
        + "border:2px solid #e9edf2;background:#fff;padding:6px;flex-shrink:0}"
        + ".hdr-business{min-width:0}"
        + ".hdr-name{font-size:1.05em;font-weight:700;color:#0f172a;line-height:1.3}"
        + ".hdr-detail{font-size:.8em;color:#64748b;line-height:1.6;margin-top:2px;word-wrap:break-word}"
        + ".hdr-r{text-align:right;flex-shrink:0}"
        + ".hdr-title{font-family:'Plus Jakarta Sans',sans-serif;font-size:1.6em;font-weight:800;"
        + "color:#0f172a;letter-spacing:-.04em;line-height:1.1;margin-bottom:8px}"
        + ".hdr-title span{color:#4f46e5}"
        + ".hdr-line{display:flex;gap:6px;justify-content:flex-end;font-size:.8em;color:#64748b;line-height:1.8}"
        + ".hdr-line .lbl{color:#94a3b8}");

        // ═══════════════════════════════════════════════════════
        // META GRID (Invoice #, Issue Date, Due Date)
        // ═══════════════════════════════════════════════════════
        h.append(
        ".meta{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;position:relative;z-index:1;padding:16px 20px;"
        + "background:#fafbfc;border:1px solid #e9edf2;border-radius:10px;margin-top:20px}"
        + ".meta-item{display:flex;flex-direction:column;gap:2px}"
        + ".meta-item .l{font-size:.65em;text-transform:uppercase;letter-spacing:.06em;color:#94a3b8;font-weight:600}"
        + ".meta-item .v{font-size:.88em;color:#0f172a;font-weight:500}");

        // ═══════════════════════════════════════════════════════
        // STATUS TRACKER
        // ═══════════════════════════════════════════════════════
        h.append(
        ".track{display:flex;align-items:center;gap:0;position:relative;z-index:1;margin-top:20px;padding:0 4px}"
        + ".track-step{display:flex;align-items:center;gap:8px;flex:1;position:relative}"
        + ".track-step:last-child{flex:0}"
        + ".track-dot{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;"
        + "font-size:.6em;font-weight:700;flex-shrink:0;border:2px solid #d1d5db;background:#fff;color:#9ca3af;position:relative;z-index:1}"
        + ".track-dot.done{border-color:#059669;background:#059669;color:#fff}"
        + ".track-dot.active{border-color:#4f46e5;background:#fff;color:#4f46e5;box-shadow:0 0 0 3px rgba(79,70,229,.15)}"
        + ".track-dot.fail{border-color:#dc2626;background:#fef2f2;color:#dc2626}"
        + ".track-line{flex:1;height:2px;background:#d1d5db;margin:0 -2px;position:relative;z-index:0;top:0}"
        + ".track-line.done{background:#059669}"
        + ".track-label{font-size:.68em;color:#94a3b8;font-weight:500;white-space:nowrap}"
        + ".track-label.done{color:#065f46}"
        + ".track-label.active{color:#4f46e5;font-weight:600}");

        // ═══════════════════════════════════════════════════════
        // ADDRESS CARDS
        // ═══════════════════════════════════════════════════════
        h.append(
        ".addr{display:grid;grid-template-columns:1fr 1fr;gap:20px;position:relative;z-index:1}"
        + ".addr-card{background:#fafbfc;border:1px solid #e9edf2;border-radius:10px;padding:16px 20px}"
        + ".addr-card .ttl{font-size:.65em;text-transform:uppercase;letter-spacing:.06em;"
        + "color:#94a3b8;font-weight:600;margin-bottom:6px}"
        + ".addr-card .ct{font-size:.85em;color:#0f172a;line-height:1.7}"
        + ".addr-card .ct strong{font-weight:600}");

        // ═══════════════════════════════════════════════════════
        // ITEMS TABLE
        // ═══════════════════════════════════════════════════════
        h.append(
        ".tw{overflow-x:auto;position:relative;z-index:1}"
        + "table{width:100%;border-collapse:collapse;font-size:.84em}"
        + "thead th{"
        + "padding:10px 8px;font-size:.63em;text-transform:uppercase;letter-spacing:.06em;"
        + "color:#94a3b8;font-weight:600;border-bottom:2px solid #e2e8f0;text-align:left;white-space:nowrap}"
        + "thead th.r{text-align:right;padding-right:4px}"
        + "thead th.c{text-align:center}"
        + "tbody tr{border-bottom:1px solid #f1f4f9;transition:background .15s}"
        + "tbody tr:last-child{border-bottom:none}"
        + "tbody tr.e{background:#fafbfc}"
        + "tbody td{padding:12px 8px;color:#1e293b;vertical-align:top;font-size:.87em}"
        + "tbody td:first-child{padding-left:0}"
        + "tbody td:last-child{padding-right:0}"
        + "tbody td.q{text-align:center;color:#64748b;font-family:'Inter',monospace;font-weight:500}"
        + "tbody td.p{text-align:right;font-variant-numeric:tabular-nums}"
        + "tbody td.t{text-align:right;font-weight:600;color:#0f172a;font-variant-numeric:tabular-nums}");

        // ═══════════════════════════════════════════════════════
        // SUMMARY + PAYMENT SIDE BY SIDE
        // ═══════════════════════════════════════════════════════
        h.append(
        ".bottom{display:grid;grid-template-columns:1fr 300px;gap:24px;margin-top:20px;position:relative;z-index:1}"
        + ".pay-section{display:flex;flex-direction:column;gap:16px}"
        + ".pay-card{background:#fafbfc;border:1px solid #e9edf2;border-radius:10px;padding:16px 20px}"
        + ".pay-card .ttl{font-size:.65em;text-transform:uppercase;letter-spacing:.06em;"
        + "color:#94a3b8;font-weight:600;margin-bottom:8px}"
        + ".pay-card .ct{font-size:.82em;color:#475569;line-height:1.7}"
        + ".pay-card .ct strong{color:#0f172a}"
        + ".pay-qr{width:100px;height:100px;border-radius:8px;border:1px solid #e9edf2;object-fit:contain;background:#fff;padding:4px}"
        + ".sum-card{"
        + "background:#fafbfc;border:1px solid #e9edf2;"
        + "border-radius:10px;padding:20px 24px;align-self:start;position:sticky;top:24px}"
        + ".sum-row{display:flex;justify-content:space-between;padding:6px 0;font-size:.84em}"
        + ".sum-row .l{color:#64748b}"
        + ".sum-row .r{color:#1e293b;font-weight:500;text-align:right;font-variant-numeric:tabular-nums}"
        + ".sum-div{height:1px;background:#e2e8f0;margin:8px 0}"
        + ".sum-total{display:flex;justify-content:space-between;padding:10px 0 0;"
        + "font-family:'Plus Jakarta Sans',sans-serif;font-size:1.2em;font-weight:800;color:#0f172a;"
        + "letter-spacing:-.02em}"
        + ".sum-total.lg{margin-top:2px}"
        + ".sum-divider{height:2px;background:#0f172a;margin:4px 0 8px}"
        + ".sum-paid{display:flex;justify-content:space-between;padding:6px 0;font-size:.84em;color:#059669}"
        + ".sum-paid .r{font-weight:600;text-align:right}"
        + ".sum-due{display:flex;justify-content:space-between;padding:6px 0;font-size:.84em;color:#dc2626;font-weight:600}");

        // ═══════════════════════════════════════════════════════
        // NOTES
        // ═══════════════════════════════════════════════════════
        h.append(
        ".notes{"
        + "background:#fafbfc;border:1px solid #e9edf2;border-radius:10px;"
        + "padding:16px 20px;font-size:.82em;color:#64748b;line-height:1.7;position:relative;z-index:1}"
        + ".notes strong{color:#0f172a}");

        // ═══════════════════════════════════════════════════════
        // REVIEW CTA
        // ═══════════════════════════════════════════════════════
        h.append(
        ".rev{text-align:center;font-size:.82em;color:#64748b;padding:12px 0 0;position:relative;z-index:1}"
        + ".rev a{color:#4f46e5;font-weight:600;text-decoration:none}"
        + ".rev a:hover{text-decoration:underline}");

        // ═══════════════════════════════════════════════════════
        // CTA BUTTONS
        // ═══════════════════════════════════════════════════════
        h.append(
        ".cta{display:flex;gap:12px;justify-content:center;margin-top:16px;position:relative;z-index:1}"
        + ".cta-btn{display:inline-flex;align-items:center;gap:8px;padding:10px 24px;border-radius:10px;"
        + "font-size:.82em;font-weight:600;font-family:'Inter',sans-serif;cursor:pointer;text-decoration:none;"
        + "transition:all .15s;border:none;line-height:1}"
        + ".cta-primary{background:#4f46e5;color:#fff}"
        + ".cta-primary:hover{background:#4338ca;box-shadow:0 2px 8px rgba(79,70,229,.3)}"
        + ".cta-secondary{background:#fff;color:#1e293b;border:1px solid #d1d5db}"
        + ".cta-secondary:hover{background:#f8fafc;border-color:#9ca3af}");

        // ═══════════════════════════════════════════════════════
        // FOOTER
        // ═══════════════════════════════════════════════════════
        h.append(
        ".ft{padding:16px 0 0;text-align:center;font-size:.75em;color:#94a3b8;"
        + "border-top:1px solid #e9edf2;margin-top:24px;position:relative;z-index:1}"
        + ".ft a{color:#4f46e5;font-weight:600;text-decoration:none}"
        + ".ft a:hover{text-decoration:underline}");

        // ═══════════════════════════════════════════════════════
        // DARK MODE
        // ═══════════════════════════════════════════════════════
        h.append(
        "@media(prefers-color-scheme:dark){"
        + "body{background:#0f172a;color:#e2e8f0}"
        + ".pg{background:#1e293b}"
        + ".hdr-name,.hdr-title,.meta-item .v,.addr-card .ct,.addr-card .ct strong,"
        + "tbody td,tbody td.t,.sum-row .r,.sum-total,.notes strong,.cta-secondary{color:#e2e8f0}"
        + ".hdr-detail,.meta-item .l,.addr-card .ttl,.addr-card .ct,"
        + "thead th,.sum-row .l,.notes,.rev,.ft{color:#94a3b8}"
        + ".meta,.addr-card,.pay-card,.sum-card,.notes{background:#1a2536;border-color:#334155}"
        + ".sep,.sep-lg,.sum-div,.ft,.sum-divider{background:#334155}"
        + "table thead th{border-bottom-color:#334155}"
        + "tbody tr.e{background:#1a2536}"
        + "tbody tr{border-bottom-color:#1e293b}"
        + ".hdr-logo,.pay-qr{border-color:#334155;background:#1e293b}"
        + ".cta-secondary{background:#1e293b;border-color:#334155}"
        + ".cta-secondary:hover{background:#1a2536}"
        + ".track-dot{border-color:#475569;background:#1e293b;color:#64748b}"
        + ".track-line{background:#475569}"
        + "}");

        // ═══════════════════════════════════════════════════════
        // PRINT
        // ═══════════════════════════════════════════════════════
        h.append(
        "@media print{"
        + "@page{margin:0}"
        + "body{background:#fff!important;padding:0!important;display:block;color:#000!important}"
        + ".pg{box-shadow:none!important;border-radius:0!important;max-width:100%!important;padding:36px 40px 24px!important}"
        + ".bdg-paid,.bdg-completed,.bdg-success,.bdg-pending,.bdg-unpaid,.bdg-partial,.bdg-partially_paid,"
        + ".bdg-cancelled,.bdg-failed,.bdg-overdue,.bdg-draft,.bdg-refunded,"
        + ".meta,.addr-card,.pay-card,.sum-card,.notes,.cta-btn"
        + "{-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important}"
        + ".cta-btn{box-shadow:none!important}"
        + ".cta{display:none!important}"
        + ".wm{display:block!important}"
        + "}");

        // ═══════════════════════════════════════════════════════
        // MOBILE
        // ═══════════════════════════════════════════════════════
        h.append(
        "@media(max-width:640px){"
        + "body{padding:12px}"
        + ".pg{padding:20px 14px 16px;border-radius:10px}"
        + ".hdr{flex-direction:column;align-items:center;text-align:center}"
        + ".hdr-l{flex-direction:column;text-align:center}"
        + ".hdr-r{text-align:center;width:100%}"
        + ".hdr-line{justify-content:center}"
        + ".meta{grid-template-columns:1fr;gap:10px;padding:14px 16px}"
        + ".addr{grid-template-columns:1fr;gap:14px}"
        + ".addr-card{padding:14px 16px}"
        + ".bottom{grid-template-columns:1fr;gap:16px}"
        + ".sum-card{position:static;width:100%}"
        + ".track{gap:4px}"
        + ".track-step{gap:4px}"
        + ".track-label{font-size:.6em}"
        + "tbody td{padding:10px 4px;font-size:.82em}"
        + "thead th{font-size:.58em;padding:8px 4px}"
        + ".notes{padding:14px 16px}"
        + ".cta{flex-direction:column;align-items:center}"
        + ".cta-btn{width:100%;justify-content:center}"
        + ".grid-2{grid-template-columns:1fr}"
        + ".pay-section{order:2}"
        + ".sum-card{order:1}"
        + "}"
        + "</style></head><body>");

        // ═════════════════════════════════════════════════════════════
        // PAGE WRAPPER
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"pg\">");

        // Watermark
        if (showWatermark) {
            h.append("<div class=\"wm\">PAID</div>");
        }

        // ═════════════════════════════════════════════════════════════
        // HEADER: Logo + Business (left), INVOICE title + status (right)
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"hdr\">")
         .append("<div class=\"hdr-l\">");
        if (!logoUrl.isEmpty()) {
            h.append("<img class=\"hdr-logo\" src=\"").append(esc(logoUrl))
             .append("\" alt=\"").append(esc(shopName)).append("\" onerror=\"this.style.display='none'\">");
        }
        h.append("<div class=\"hdr-business\">")
         .append("<div class=\"hdr-name\">").append(esc(shopName)).append("</div>")
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
         .append("<div class=\"hdr-r\">")
         .append("<div class=\"hdr-title\"><span>IN</span>VOICE</div>")
         .append("<span class=\"bdg bdg-").append(psClass).append("\">")
         .append("<span class=\"bdg-dot\"></span>").append(esc(psLabelDisplay))
         .append("</span>")
         .append("</div></div>");

        // ═════════════════════════════════════════════════════════════
        // META GRID: Invoice #, Issue Date, Payment Date
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"meta\">")
         .append("<div class=\"meta-item\"><span class=\"l\">Invoice Number</span><span class=\"v\">").append(esc(orderCode)).append("</span></div>")
         .append("<div class=\"meta-item\"><span class=\"l\">Issue Date</span><span class=\"v\">").append(esc(date)).append("</span></div>")
         .append("<div class=\"meta-item\"><span class=\"l\">").append(isPaid ? "Payment Date" : "Payment Status").append("</span><span class=\"v\">").append(isPaid ? esc(paidDate) : esc(psLabelDisplay)).append("</span></div>")
         .append("</div>");

        // ═════════════════════════════════════════════════════════════
        // STATUS TRACKER
        // ═════════════════════════════════════════════════════════════
        boolean isCancelled = ps.equals("cancelled") || ps.equals("failed") || ps.equals("refunded");
        boolean isPending = ps.equals("pending") || ps.equals("unpaid") || ps.equals("partial") || ps.equals("partially_paid");
        String s1 = "done", s2 = "", s3 = "";
        if (isPaid || ps.equals("completed") || ps.equals("success")) {
            s1 = "done"; s2 = "done"; s3 = "done";
        } else if (isCancelled) {
            s1 = "done"; s2 = "fail"; s3 = "fail";
        } else if (isPending) {
            s1 = "done"; s2 = "active"; s3 = "";
        } else if (ps.equals("draft")) {
            s1 = ""; s2 = ""; s3 = "";
        }

        h.append("<div class=\"track\">")
         .append("<div class=\"track-step\"><div class=\"track-dot ").append(s1).append("\">&#10003;</div>")
         .append("<span class=\"track-label ").append(s1).append("\">Created</span></div>")
         .append("<div class=\"track-line ").append(s2.equals("done") ? "done" : s2.equals("fail") ? "" : "").append("\"></div>")
         .append("<div class=\"track-step\"><div class=\"track-dot ").append(s2.equals("active") ? "active" : s2).append("\">").append(s2.equals("done") ? "&#10003;" : s2.equals("fail") ? "&#10007;" : "02").append("</div>")
         .append("<span class=\"track-label ").append(s2).append("\">").append(isCancelled ? "Cancelled" : "Processed").append("</span></div>")
         .append("<div class=\"track-line ").append(s3.equals("done") ? "done" : "").append("\"></div>")
         .append("<div class=\"track-step\"><div class=\"track-dot ").append(s3).append("\">").append(s3.equals("done") ? "&#10003;" : "03").append("</div>")
         .append("<span class=\"track-label ").append(s3).append("\">").append(isCancelled ? "Failed" : "Completed").append("</span></div>")
         .append("</div>");

        // ═════════════════════════════════════════════════════════════
        // DIVIDER
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"sep-lg\"></div>");

        // ═════════════════════════════════════════════════════════════
        // ADDRESSES: From / Bill To
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"addr\">")
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

        // ═════════════════════════════════════════════════════════════
        // ITEMS TABLE
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"tw mt20\"><table><thead><tr>")
         .append("<th style=\"width:40%\">Item</th><th class=\"c\" style=\"width:8%\">Qty</th>")
         .append("<th class=\"r\" style=\"width:17%\">Rate</th><th class=\"r\" style=\"width:17%\">GST</th><th class=\"r\" style=\"width:18%\">Amount</th>")
         .append("</tr></thead><tbody>").append(rows)
         .append("</tbody></table></div>");

        // ═════════════════════════════════════════════════════════════
        // BOTTOM: Payment Info (left) + Summary (right)
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"bottom\">")
         .append("<div class=\"pay-section\">");

        // Payment method card
        h.append("<div class=\"pay-card\">")
         .append("<div class=\"ttl\">Payment</div>")
         .append("<div class=\"ct\">")
         .append(paymentIcon(paymentMode)).append(" <strong>").append(esc(paymentMode)).append("</strong><br>");
        if (isPaid && !paidDate.isEmpty()) {
            h.append("Paid on ").append(esc(paidDate)).append("<br>");
        }
        h.append("</div></div>");

        // UPI QR
        if (!upiQr.isEmpty() || !upiHandle.isEmpty()) {
            h.append("<div class=\"pay-card\">")
             .append("<div class=\"ttl\">UPI Payment</div>")
             .append("<div class=\"ct\">");
            if (!upiQr.isEmpty()) {
                h.append("<div class=\"mb8\"><img class=\"pay-qr\" src=\"").append(esc(upiQr))
                 .append("\" alt=\"UPI QR\" onerror=\"this.style.display='none'\"></div>");
            }
            if (!upiHandle.isEmpty()) {
                h.append("<strong>UPI ID:</strong> ").append(esc(upiHandle)).append("<br>");
            }
            if (!upiMobile.isEmpty()) {
                h.append("<strong>Mobile:</strong> ").append(esc(upiMobile));
            }
            h.append("</div></div>");
        }

        h.append("</div>"); // close pay-section

        // Summary card
        h.append("<div class=\"sum-card\">")
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
        h.append("<div class=\"sum-divider\"></div>")
         .append("<div class=\"sum-total\"><span>Total Amount</span><span>").append(totalStr).append("</span></div>");
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            h.append("<div class=\"sum-div\"></div>")
             .append("<div class=\"sum-paid\"><span>Paid</span><span class=\"r\">").append(paidStr).append("</span></div>");
        }
        if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            h.append("<div class=\"sum-due\"><span>Balance Due</span><span>").append(balanceStr).append("</span></div>");
        }
        h.append("</div>") // close sum-card
         .append("</div>"); // close bottom

        // ═════════════════════════════════════════════════════════════
        // NOTES / TERMS
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"mt24\"><div class=\"notes\">")
         .append("<strong>Notes &amp; Terms</strong><br>")
         .append(esc(footer.isEmpty() ? "Thank you for your business!" : footer))
         .append("</div></div>");

        // ═════════════════════════════════════════════════════════════
        // REVIEW CTA
        // ═════════════════════════════════════════════════════════════
        if (!reviewUrl.isEmpty()) {
            h.append("<div class=\"rev\">")
             .append("\u2B50 How was your experience? <a href=\"").append(esc(reviewUrl)).append("\" target=\"_blank\">Leave a review</a>")
             .append("</div>");
        }

        // ═════════════════════════════════════════════════════════════
        // CTA BUTTONS
        // ═════════════════════════════════════════════════════════════
        h.append("<div class=\"cta\">")
         .append("<button class=\"cta-btn cta-secondary\" onclick=\"window.print()\">")
         .append("<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
         + "<path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"/><polyline points=\"7 10 12 15 17 10\"/><line x1=\"12\" y1=\"15\" x2=\"12\" y2=\"3\"/></svg>")
         .append("Download PDF</button>");
        h.append("</div>");

        // ═════════════════════════════════════════════════════════════
        // FOOTER
        // ═════════════════════════════════════════════════════════════
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
