package com.khanabook.saas.controller;

import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.PermissionService;
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

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final BillItemRepository billItemRepository;
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
                               PermissionService permissionService) {
        this.billItemRepository = billItemRepository;
        this.billRepository = billRepository;
        this.itemRecipeRepository = itemRecipeRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.menuItemRepository = menuItemRepository;
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
        Range range = range(from, to);

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
        LocalDate day = LocalDate.parse(date);
        long from = day.atStartOfDay(ZONE).toInstant().toEpochMilli();
        long to = day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();

        long[] buckets = new long[24];
        billItemRepository.findByRestaurantIdAndCreatedAtBetween(restaurantId, from, to)
                .forEach(bi -> {
                    if (bi.getCreatedAt() == null || bi.getQuantity() == null) return;
                    int hour = Instant.ofEpochMilli(bi.getCreatedAt()).atZone(ZONE).getHour();
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
        Range range = range(from, to);

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
            BigDecimal revenue = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

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
        LocalDate day = LocalDate.parse(date);
        long from = day.atStartOfDay(ZONE).toInstant().toEpochMilli();
        long to = day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();

        // Payment mode breakdown: [mode, status, count]
        List<Object[]> modeStatus = billRepository.countByModeAndStatusBetween(from, to);
        long totalOrders = 0;
        long completedOrders = 0;
        long cancelledOrders = 0;
        long draftOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal refundedAmount = BigDecimal.ZERO;
        Map<String, long[]> modeSplits = new LinkedHashMap<>();

        for (Object[] row : modeStatus) {
            String mode = row[0] != null ? row[0].toString() : "unknown";
            String status = row[1] != null ? row[1].toString().toLowerCase() : "";
            long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            totalOrders += count;
            if ("completed".equals(status) || "paid".equals(status)) {
                completedOrders += count;
                modeSplits.computeIfAbsent(mode, k -> new long[]{0, 0})[0] += count;
            } else if ("cancelled".equals(status)) {
                cancelledOrders += count;
            } else if ("draft".equals(status)) {
                draftOrders += count;
            } else if ("refunded".equals(status)) {
                cancelledOrders += count;
            }
        }

        // Revenue from successful bills
        List<Object[]> successfulByMode = billRepository.countSuccessfulByModeBetween(from, to);
        for (Object[] row : successfulByMode) {
            String mode = row[0] != null ? row[0].toString() : "unknown";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            modeSplits.computeIfAbsent(mode, k -> new long[]{0, 0});
        }

        // Get actual revenue from bill items in range
        var billItems = billItemRepository.findByRestaurantIdAndCreatedAtBetween(restaurantId, from, to);
        for (var bi : billItems) {
            if (bi.getItemTotal() != null) {
                totalRevenue = totalRevenue.add(bi.getItemTotal());
            }
        }

        // Payment splits with revenue
        List<Map<String, Object>> paymentSplits = new ArrayList<>();
        BigDecimal expectedCash = BigDecimal.ZERO;
        for (Map.Entry<String, long[]> entry : modeSplits.entrySet()) {
            Map<String, Object> split = new LinkedHashMap<>();
            split.put("mode", entry.getKey());
            split.put("count", entry.getValue()[0]);
            paymentSplits.add(split);
            if (entry.getKey().toLowerCase().contains("cash")) {
                // Approximate cash amount (count * avg would need per-bill query)
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("totalOrders", totalOrders);
        result.put("completedOrders", completedOrders);
        result.put("cancelledOrders", cancelledOrders);
        result.put("draftOrders", draftOrders);
        result.put("totalRevenue", totalRevenue);
        result.put("paymentSplits", paymentSplits);
        return ResponseEntity.ok(result);
    }

    private static Range range(String from, String to) {
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to).plusDays(1); // exclusive end
        return new Range(f.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                t.atStartOfDay(ZONE).toInstant().toEpochMilli());
    }

    private Long requireTenant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "No restaurant context");
        }
        return restaurantId;
    }
}
