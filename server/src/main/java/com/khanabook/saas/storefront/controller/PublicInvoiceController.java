package com.khanabook.saas.storefront.controller;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Public, no-auth endpoint that renders an HTML invoice page.
 * URL shared via WhatsApp:
 *   {BACKEND_URL}/public/invoice/{restaurantId}/{billId}
 *
 * Returns a self-contained HTML document so the customer can view the
 * invoice in any browser — no app required.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicInvoiceController {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;

    @GetMapping(value = "/invoice/{restaurantId}/{billId}",
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getInvoice(@PathVariable Long restaurantId,
                                             @PathVariable Long billId) {
        Optional<Bill> billOpt = billRepository.findById(billId);
        if (billOpt.isEmpty()) {
            return ResponseEntity.status(404).body(notFound());
        }
        Bill bill = billOpt.get();
        if (Boolean.TRUE.equals(bill.getIsDeleted())
                || !restaurantId.equals(bill.getRestaurantId())) {
            return ResponseEntity.status(404).body(notFound());
        }

        RestaurantProfile profile = restaurantProfileRepository
                .findByRestaurantId(restaurantId)
                .orElse(null);

        List<BillItem> items = billItemRepository
                .findByServerBillIdAndIsDeletedFalseOrderById(billId);

        return ResponseEntity.ok(render(bill, items, profile));
    }

    private String render(Bill bill, List<BillItem> items, RestaurantProfile profile) {
        String shopName = profile != null && profile.getShopName() != null
                ? profile.getShopName() : "Invoice";
        String currency = profile != null
                && ("INR".equals(profile.getCurrency()) || "Rupee".equals(profile.getCurrency()))
                ? "Rs." : (profile != null && profile.getCurrency() != null ? profile.getCurrency() : "Rs.");
        boolean gstEnabled = profile != null && Boolean.TRUE.equals(profile.getGstEnabled());
        String tz = profile != null && profile.getTimezone() != null
                ? profile.getTimezone() : "Asia/Kolkata";

        String date = DateTimeFormatter
                .ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                .withZone(ZoneId.of(tz))
                .format(Instant.ofEpochMilli(bill.getCreatedAt() != null ? bill.getCreatedAt() : 0L));

        String orderNo = bill.getDailyOrderDisplay() == null ? ""
                : bill.getDailyOrderDisplay().substring(
                        bill.getDailyOrderDisplay().lastIndexOf('-') + 1);

        StringBuilder rows = new StringBuilder();
        for (BillItem item : items) {
            String name = esc(item.getItemName());
            if (item.getVariantName() != null && !item.getVariantName().isBlank()) {
                name += " (" + esc(item.getVariantName()) + ")";
            }
            rows.append("<tr><td>").append(name)
                    .append("</td><td class='c'>").append(item.getQuantity())
                    .append("</td><td class='r'>").append(currency).append(' ')
                    .append(money(item.getItemTotal()))
                    .append("</td></tr>");
        }

        String invLabel = gstEnabled ? "Tax Invoice No" : "Invoice No";
        String address = profile != null && profile.getShopAddress() != null
                ? esc(profile.getShopAddress()) : "";
        String gstinRow = profile != null && profile.getGstin() != null && !profile.getGstin().isBlank()
                ? "<div class='gstin'>GSTIN: " + esc(profile.getGstin()) + "</div>" : "";

        return "<!doctype html><html lang='en'><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(shopName) + " — Invoice INV" + bill.getLifetimeOrderId() + "</title>"
                + "<style>"
                + "*{box-sizing:border-box}"
                + "body{font-family:-apple-system,system-ui,Segoe UI,Roboto,sans-serif;"
                + "background:#f5f5f5;margin:0;padding:16px;color:#222}"
                + ".card{max-width:480px;margin:0 auto;background:#fff;border-radius:12px;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.08);padding:24px}"
                + ".shop{font-size:22px;font-weight:700;text-align:center;margin-bottom:4px}"
                + ".addr{text-align:center;color:#555;font-size:13px;margin-bottom:4px}"
                + ".gstin{text-align:center;color:#555;font-size:12px;margin-bottom:12px}"
                + "hr{border:0;border-top:1px dashed #ccc;margin:12px 0}"
                + ".meta{display:flex;justify-content:space-between;font-size:13px;margin:4px 0}"
                + ".meta span:first-child{color:#666}"
                + "table{width:100%;border-collapse:collapse;margin-top:8px;font-size:14px}"
                + "th,td{padding:6px 4px;border-bottom:1px solid #eee;text-align:left}"
                + "th{font-size:12px;color:#666;text-transform:uppercase;letter-spacing:.5px}"
                + ".c{text-align:center}.r{text-align:right;white-space:nowrap}"
                + ".total{display:flex;justify-content:space-between;font-size:18px;"
                + "font-weight:700;margin-top:14px;padding-top:12px;border-top:2px solid #222}"
                + ".thanks{text-align:center;color:#666;font-size:13px;margin-top:16px}"
                + "</style></head><body><div class='card'>"
                + "<div class='shop'>" + esc(shopName) + "</div>"
                + (address.isEmpty() ? "" : "<div class='addr'>" + address + "</div>")
                + gstinRow
                + "<hr>"
                + "<div class='meta'><span>Order ID</span><span>#" + esc(orderNo) + "</span></div>"
                + "<div class='meta'><span>" + invLabel + "</span><span>INV" + bill.getLifetimeOrderId() + "</span></div>"
                + "<div class='meta'><span>Date</span><span>" + date + "</span></div>"
                + "<table><thead><tr><th>Item</th><th class='c'>Qty</th><th class='r'>Total</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>"
                + "<div class='total'><span>Total</span><span>" + currency + ' ' + money(bill.getTotalAmount()) + "</span></div>"
                + "<div class='thanks'>Thank you for your visit!</div>"
                + "</div></body></html>";
    }

    private String notFound() {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<title>Invoice not found</title>"
                + "<style>body{font-family:sans-serif;text-align:center;padding:48px;color:#555}</style>"
                + "</head><body><h2>Invoice not found</h2>"
                + "<p>This invoice link is invalid or has been removed.</p></body></html>";
    }

    private String money(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
