package com.khanabook.saas.feature.reports.controller;

import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.staff.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side sales analytics (P0 reports parity). Tenant-scoped by JWT.
 * Food-cost is only computable here: recipes and material costs are
 * server-owned data (V81).
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final BillItemRepository billItemRepository;
    private final com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository profileRepository;
    private final BillRepository billRepository;
    private final ItemRecipeRepository itemRecipeRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final MenuItemRepository menuItemRepository;
    private final PermissionService permissionService;

    public AnalyticsController(BillItemRepository billItemRepository,
                               BillRepository billRepository,
                               ItemRecipeRepository itemRecipeRepository,
                               RawMaterialRepository rawMaterialRepository,
                               MenuItemRepository menuItemRepository,
                               com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository profileRepository,
                               PermissionService permissionService) {
        this.billItemRepository = billItemRepository;
        this.billRepository = billRepository;
        this.itemRecipeRepository = itemRecipeRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.menuItemRepository = menuItemRepository;
        this.profileRepository = profileRepository;
        this.permissionService = permissionService;
    }

    private void requirePermission(String permissionKey) {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        if (restaurantId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!permissionService.hasPermission(restaurantId, userId, permissionKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + permissionKey);
        }
    }

    /** Item-wise sales: quantity + revenue per menu item for a date range. */
    @GetMapping("/item-sales")
    public ResponseEntity<List<Map<String, Object>>> itemSales(
            @RequestParam String from, @RequestParam String to) {
        requirePermission("reports.day_summary");
        Long restaurantId = requireTenant();
        Range range = range(from, to, dayContext(restaurantId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : billItemRepository.aggregateSalesByMenuItem(
                restaurantId, range.from(), range.to())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("menuItemId", row[0]);
            m.put("name", row[1]);
            m.put("quantitySold", row[2] != null ? ((Number) row[2]).longValue() : 0L);
            m.put("revenue", row[3] != null ? row[3] : BigDecimal.ZERO);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    /** Hourly sales curve for a single day (completed bills by created hour). */
    @GetMapping("/hourly-sales")
    public ResponseEntity<List<Map<String, Object>>> hourlySales(@RequestParam String date) {
        requirePermission("reports.day_summary");
        Long restaurantId = requireTenant();
        var dayContext = dayContext(restaurantId);
        LocalDate day = LocalDate.parse(date);
        long from = com.khanabook.saas.core.utility.BusinessDay.windowStart(
                day, dayContext.zone(), dayContext.cutoff(), dayContext.effectiveFrom());
        long to = com.khanabook.saas.core.utility.BusinessDay.windowEnd(
                day, dayContext.zone(), dayContext.cutoff(), dayContext.effectiveFrom());

        long[] buckets = new long[24];
        billItemRepository.findByRestaurantIdAndCreatedAtBetween(restaurantId, from, to)
                .forEach(bi -> {
                    if (bi.getCreatedAt() == null || bi.getQuantity() == null) return;
                    int hour = Instant.ofEpochMilli(bi.getCreatedAt()).atZone(dayContext.zone()).getHour();
                    buckets[hour] += bi.getQuantity();
                });

        List<Map<String, Object>> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", h);
            m.put("itemsSold", buckets[h]);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Food-cost per item: recipe material cost vs revenue for the range.
     * Items without recipes are reported with null cost (not yet configured).
     */
    @GetMapping("/food-cost")
    public ResponseEntity<List<Map<String, Object>>> foodCost(
            @RequestParam String from, @RequestParam String to) {
        requirePermission("reports.full");
        Long restaurantId = requireTenant();
        Range range = range(from, to, dayContext(restaurantId));

        // Material cost lookup
        Map<Long, BigDecimal> costPerUnit = new LinkedHashMap<>();
        rawMaterialRepository.findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(restaurantId)
                .forEach(m -> costPerUnit.put(m.getId(),
                        m.getCostPerUnit() != null ? m.getCostPerUnit() : BigDecimal.ZERO));

        // Recipe map: menuItemId -> (materialId -> qtyPerItem)
        Map<Long, Map<Long, BigDecimal>> recipes = new LinkedHashMap<>();
        for (ItemRecipe r : itemRecipeRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId)) {
            recipes.computeIfAbsent(r.getMenuItemId(), k -> new LinkedHashMap<>())
                    .put(r.getRawMaterial().getId(), r.getQuantityPerItem());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : billItemRepository.aggregateSalesByMenuItem(
                restaurantId, range.from(), range.to())) {
            Long menuItemId = (Long) row[0];
            long qty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal revenue = toBigDecimal(row[3]);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("menuItemId", menuItemId);
            m.put("name", row[1]);
            m.put("quantitySold", qty);
            m.put("revenue", revenue);

            Map<Long, BigDecimal> recipe = recipes.get(menuItemId);
            if (recipe == null || recipe.isEmpty()) {
                m.put("cost", null);
                m.put("configured", false);
            } else {
                BigDecimal unitCost = BigDecimal.ZERO;
                for (Map.Entry<Long, BigDecimal> e : recipe.entrySet()) {
                    unitCost = unitCost.add(
                            costPerUnit.getOrDefault(e.getKey(), BigDecimal.ZERO).multiply(e.getValue()));
                }
                BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(qty));
                m.put("cost", totalCost);
                m.put("configured", true);
                m.put("marginPct", revenue.signum() > 0
                        ? revenue.subtract(totalCost)
                                .divide(revenue, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO);
            }
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    private record Range(long from, long to) {}

    /** Daily closing: revenue summary, payment splits, expected cash for a date. */
    @GetMapping("/daily-closing")
    public ResponseEntity<Map<String, Object>> dailyClosing(@RequestParam String date) {
        requirePermission("reports.day_summary");
        Long restaurantId = requireTenant();
        var dayContext = dayContext(restaurantId);
        LocalDate day = LocalDate.parse(date);
        long from = com.khanabook.saas.core.utility.BusinessDay.windowStart(
                day, dayContext.zone(), dayContext.cutoff(), dayContext.effectiveFrom());
        long to = com.khanabook.saas.core.utility.BusinessDay.windowEnd(
                day, dayContext.zone(), dayContext.cutoff(), dayContext.effectiveFrom());

        // Payment mode breakdown: [mode, status, count]
        List<Object[]> modeStatus = billRepository.countByRestaurantIdAndModeAndStatusBetween(restaurantId, from, to);
        long totalOrders = 0;
        long completedOrders = 0;
        long cancelledOrders = 0;
        long draftOrders = 0;

        for (Object[] row : modeStatus) {
            String status = row[1] != null ? row[1].toString().toLowerCase() : "";
            long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            totalOrders += count;
            if ("completed".equals(status) || "paid".equals(status)) {
                completedOrders += count;
            } else if ("cancelled".equals(status)) {
                cancelledOrders += count;
            } else if ("draft".equals(status)) {
                draftOrders += count;
            } else if ("refunded".equals(status)) {
                cancelledOrders += count;
            }
        }

        // Authoritative revenue from successful bills (scoped to restaurantId)
        BigDecimal totalRevenue = billRepository.sumRevenueByRestaurantIdBetween(restaurantId, from, to);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal refundedAmount = billRepository.sumRefundByRestaurantIdBetween(restaurantId, from, to);
        if (refundedAmount == null) refundedAmount = BigDecimal.ZERO;

        BigDecimal netRevenue = totalRevenue.subtract(refundedAmount);
        if (netRevenue.compareTo(BigDecimal.ZERO) < 0) netRevenue = BigDecimal.ZERO;

        // Payment splits with count and revenue amount per mode: [mode, count, sumAmount]
        List<Object[]> successfulByMode = billRepository.sumSuccessfulByRestaurantIdAndModeBetween(restaurantId, from, to);
        List<Map<String, Object>> paymentSplits = new ArrayList<>();
        BigDecimal expectedCash = BigDecimal.ZERO;

        for (Object[] row : successfulByMode) {
            String mode = row[0] != null ? row[0].toString() : "unknown";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal amount = toBigDecimal(row[2]);

            Map<String, Object> split = new LinkedHashMap<>();
            split.put("mode", mode);
            split.put("count", count);
            split.put("total", amount);
            paymentSplits.add(split);

            if (mode.toLowerCase().contains("cash")) {
                expectedCash = expectedCash.add(amount);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("totalOrders", totalOrders);
        result.put("completedOrders", completedOrders);
        result.put("cancelledOrders", cancelledOrders);
        result.put("draftOrders", draftOrders);
        result.put("totalRevenue", totalRevenue);
        result.put("refundedAmount", refundedAmount);
        result.put("netRevenue", netRevenue);
        result.put("expectedCash", expectedCash);
        result.put("paymentSplits", paymentSplits);
        return ResponseEntity.ok(result);
    }

    /** GST compliance ledger: taxable turnover, CGST, SGST, total tax, invoice breakdown */
    @GetMapping("/gst-ledger")
    public ResponseEntity<Map<String, Object>> gstLedger(
            @RequestParam String from, @RequestParam String to) {
        requirePermission("reports.day_summary");
        Long restaurantId = requireTenant();
        Range range = range(from, to, dayContext(restaurantId));

        List<com.khanabook.saas.feature.billing.data.Bill> bills =
                billRepository.findByRestaurantIdAndCreatedAtBetweenAndIsDeletedFalse(restaurantId, range.from(), range.to());

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        List<Map<String, Object>> entries = new ArrayList<>();

        for (var b : bills) {
            if ("cancelled".equalsIgnoreCase(b.getOrderStatus())) continue;
            BigDecimal cgst = b.getCgstAmount() != null ? b.getCgstAmount() : BigDecimal.ZERO;
            BigDecimal sgst = b.getSgstAmount() != null ? b.getSgstAmount() : BigDecimal.ZERO;
            BigDecimal tax = cgst.add(sgst);
            BigDecimal subtotal = b.getSubtotal() != null ? b.getSubtotal() : b.getTotalAmount();
            if (subtotal == null) subtotal = BigDecimal.ZERO;

            totalTaxable = totalTaxable.add(subtotal);
            totalCgst = totalCgst.add(cgst);
            totalSgst = totalSgst.add(sgst);
            totalTax = totalTax.add(tax);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("billId", b.getId());
            entry.put("invoiceNumber", b.getInvoiceNumber() != null ? b.getInvoiceNumber() : (b.getDailyOrderDisplay() != null ? b.getDailyOrderDisplay() : String.valueOf(b.getDailyOrderId())));
            entry.put("publicToken", b.getPublicToken() != null ? b.getPublicToken().toString() : null);
            entry.put("customerName", b.getCustomerName());
            entry.put("subtotal", subtotal);
            entry.put("gstRate", b.getGstPercentage() != null ? b.getGstPercentage() : BigDecimal.ZERO);
            entry.put("cgst", cgst);
            entry.put("sgst", sgst);
            entry.put("totalTax", tax);
            entry.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO);
            entry.put("createdAt", b.getCreatedAt());
            entries.add(entry);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalTaxable", totalTaxable);
        out.put("totalCgst", totalCgst);
        out.put("totalSgst", totalSgst);
        out.put("totalTax", totalTax);
        out.put("invoiceCount", entries.size());
        out.put("entries", entries);
        return ResponseEntity.ok(out);
    }

    /**
     * The restaurant's own business day: its timezone, cutoff and effective instant.
     *
     * <p>Reports used to hardcode {@code Asia/Kolkata} and a midnight-to-midnight window,
     * which stops matching the day the bills were numbered under as soon as a cutoff is
     * configured — the 01:00 sales would land in the wrong day's report.
     */
    private record DayContext(ZoneId zone, java.time.LocalTime cutoff, Long effectiveFrom) {}

    private DayContext dayContext(Long restaurantId) {
        var profile = profileRepository.findByRestaurantId(restaurantId).orElse(null);
        return new DayContext(
                com.khanabook.saas.core.utility.BusinessDay.zoneOf(
                        profile != null ? profile.getTimezone() : null),
                profile != null && profile.getBusinessDayCutoffTime() != null
                        ? profile.getBusinessDayCutoffTime()
                        : java.time.LocalTime.MIDNIGHT,
                profile != null ? profile.getCutoffEffectiveFrom() : null);
    }

    private static Range range(String from, String to, DayContext ctx) {
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        return new Range(
                com.khanabook.saas.core.utility.BusinessDay.windowStart(
                        f, ctx.zone(), ctx.cutoff(), ctx.effectiveFrom()),
                com.khanabook.saas.core.utility.BusinessDay.windowEnd(
                        t, ctx.zone(), ctx.cutoff(), ctx.effectiveFrom()));
    }

    private Long requireTenant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "No restaurant context");
        }
        return restaurantId;
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(obj.toString());
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }
}
