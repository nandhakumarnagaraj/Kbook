package com.khanabook.saas.controller;

import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.entity.StockMovement;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import com.khanabook.saas.repository.StockMovementRepository;
import com.khanabook.saas.repository.VendorRepository;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Recipe-based raw-material inventory (Phase 3 + Plan 05 loop).
 * Tenant-scoped by JWT; management actions are OWNER-only, reads allowed
 * for all staff.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final RawMaterialRepository rawMaterialRepository;
    private final ItemRecipeRepository itemRecipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final VendorRepository vendorRepository;
    private final InventoryService inventoryService;
    private final com.khanabook.saas.repository.PurchaseOrderRepository purchaseOrderRepository;

    public InventoryController(RawMaterialRepository rawMaterialRepository,
                               ItemRecipeRepository itemRecipeRepository,
                               StockMovementRepository stockMovementRepository,
                               VendorRepository vendorRepository,
                               com.khanabook.saas.repository.PurchaseOrderRepository purchaseOrderRepository,
                               InventoryService inventoryService) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.itemRecipeRepository = itemRecipeRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.vendorRepository = vendorRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.inventoryService = inventoryService;
    }

    // ── Raw materials ─────────────────────────────────────────────────────

    @GetMapping("/materials")
    public ResponseEntity<List<RawMaterial>> listMaterials() {
        Long restaurantId = requireTenant();
        return ResponseEntity.ok(rawMaterialRepository
                .findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(restaurantId));
    }

    @PostMapping("/materials")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> createMaterial(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (rawMaterialRepository.findByRestaurantIdAndNameAndIsDeletedFalse(restaurantId, name).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "MATERIAL_EXISTS"));
        }
        RawMaterial material = new RawMaterial();
        material.setRestaurantId(restaurantId);
        material.setName(name.trim());
        material.setUnit(strOr(body.get("unit"), "kg"));
        material.setStockQuantity(decOr(body.get("stockQuantity"), java.math.BigDecimal.ZERO));
        material.setLowStockThreshold(decOr(body.get("lowStockThreshold"), java.math.BigDecimal.ZERO));
        material.setCostPerUnit(decOrNull(body.get("costPerUnit")));
        long now = System.currentTimeMillis();
        material.setCreatedAt(now);
        material.setUpdatedAt(now);
        return ResponseEntity.ok(rawMaterialRepository.save(material));
    }

    @PutMapping("/materials/{id}")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> updateMaterial(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        return rawMaterialRepository.findById(id)
                .filter(m -> m.getRestaurantId().equals(restaurantId) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .map(material -> {
                    if (body.containsKey("name") && str(body.get("name")) != null) {
                        material.setName(str(body.get("name")).trim());
                    }
                    if (body.containsKey("unit") && str(body.get("unit")) != null) {
                        material.setUnit(str(body.get("unit")));
                    }
                    if (body.containsKey("stockQuantity")) {
                        material.setStockQuantity(decOr(body.get("stockQuantity"), material.getStockQuantity()));
                    }
                    if (body.containsKey("lowStockThreshold")) {
                        material.setLowStockThreshold(
                                decOr(body.get("lowStockThreshold"), material.getLowStockThreshold()));
                    }
                    if (body.containsKey("costPerUnit")) {
                        material.setCostPerUnit(decOrNull(body.get("costPerUnit")));
                    }
                    material.setUpdatedAt(System.currentTimeMillis());
                    return ResponseEntity.ok(rawMaterialRepository.save(material));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/materials/{id}")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> deleteMaterial(@PathVariable Long id) {
        Long restaurantId = requireTenant();
        return rawMaterialRepository.findById(id)
                .filter(m -> m.getRestaurantId().equals(restaurantId))
                .map(material -> {
                    material.setIsDeleted(true);
                    material.setUpdatedAt(System.currentTimeMillis());
                    rawMaterialRepository.save(material);
                    return ResponseEntity.ok(Map.of("status", "deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Recipes ───────────────────────────────────────────────────────────

    @GetMapping("/recipes/{menuItemId}")
    public ResponseEntity<List<ItemRecipe>> listRecipes(@PathVariable Long menuItemId) {
        Long restaurantId = requireTenant();
        return ResponseEntity.ok(itemRecipeRepository
                .findByRestaurantIdAndMenuItemIdAndIsDeletedFalse(restaurantId, menuItemId));
    }

    @PostMapping("/recipes")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> addRecipeLine(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        Long menuItemId = longOrNull(body.get("menuItemId"));
        Long rawMaterialId = longOrNull(body.get("rawMaterialId"));
        java.math.BigDecimal quantityPerItem = decOrNull(body.get("quantityPerItem"));
        if (menuItemId == null || rawMaterialId == null
                || quantityPerItem == null || quantityPerItem.signum() <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "menuItemId, rawMaterialId and positive quantityPerItem are required"));
        }
        var material = rawMaterialRepository.findById(rawMaterialId)
                .filter(m -> m.getRestaurantId().equals(restaurantId) && !Boolean.TRUE.equals(m.getIsDeleted()));
        if (material.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "rawMaterialId not found"));
        }
        ItemRecipe recipe = new ItemRecipe();
        recipe.setRestaurantId(restaurantId);
        recipe.setMenuItemId(menuItemId);
        recipe.setRawMaterial(material.get());
        recipe.setQuantityPerItem(quantityPerItem);
        long now = System.currentTimeMillis();
        recipe.setCreatedAt(now);
        recipe.setUpdatedAt(now);
        return ResponseEntity.ok(itemRecipeRepository.save(recipe));
    }

    @DeleteMapping("/recipes/{id}")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> deleteRecipeLine(@PathVariable Long id) {
        Long restaurantId = requireTenant();
        return itemRecipeRepository.findById(id)
                .filter(r -> r.getRestaurantId().equals(restaurantId))
                .map(recipe -> {
                    recipe.setIsDeleted(true);
                    recipe.setUpdatedAt(System.currentTimeMillis());
                    itemRecipeRepository.save(recipe);
                    return ResponseEntity.ok(Map.of("status", "deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Stock loop: purchase / wastage / physical count / movements ───────

    /** Stock-in: delivery arrived. Updates stock + weighted-avg cost + expiry. */
    @PostMapping("/purchase")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> purchase(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        try {
            Long materialId = longOrNull(body.get("materialId"));
            java.math.BigDecimal qty = decOrNull(body.get("quantity"));
            if (materialId == null || qty == null || qty.signum() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "materialId and positive quantity required"));
            }
            return ResponseEntity.ok(inventoryService.purchase(restaurantId, materialId, qty,
                    decOrNull(body.get("unitCost")), longOrNull(body.get("vendorId")),
                    longOrNull(body.get("expiryDate"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Wastage: spoilage/spillage/tasting — reason mandatory. */
    @PostMapping("/wastage")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> wastage(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        try {
            Long materialId = longOrNull(body.get("materialId"));
            java.math.BigDecimal qty = decOrNull(body.get("quantity"));
            String reason = str(body.get("reason"));
            if (materialId == null || qty == null || qty.signum() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "materialId and positive quantity required"));
            }
            return ResponseEntity.ok(inventoryService.wastage(restaurantId, materialId, qty, reason));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evening physical count. Returns the variance (counted - system);
     * writes an ADJUST movement so the ledger stays truthful.
     */
    @PostMapping("/physical-count")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> physicalCount(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        try {
            Long materialId = longOrNull(body.get("materialId"));
            java.math.BigDecimal counted = decOrNull(body.get("countedQty"));
            if (materialId == null || counted == null || counted.signum() < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "materialId and countedQty >= 0 required"));
            }
            return ResponseEntity.ok(inventoryService.adjustPhysicalCount(
                    restaurantId, materialId, counted, TenantContext.getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Movement history per material (the audit trail). */
    @GetMapping("/movements/{materialId}")
    public ResponseEntity<List<StockMovement>> movements(@PathVariable Long materialId) {
        Long restaurantId = requireTenant();
        return ResponseEntity.ok(stockMovementRepository
                .findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(restaurantId, materialId));
    }

    // ── Vendors ───────────────────────────────────────────────────────────

    @GetMapping("/vendors")
    public ResponseEntity<?> listVendors() {
        return ResponseEntity.ok(vendorRepository
                .findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(requireTenant()));
    }

    @PostMapping("/vendors")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> createVendor(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        com.khanabook.saas.entity.Vendor v = new com.khanabook.saas.entity.Vendor();
        v.setRestaurantId(restaurantId);
        v.setName(name.trim());
        v.setPhone(str(body.get("phone")));
        v.setNotes(str(body.get("notes")));
        long now = System.currentTimeMillis();
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        return ResponseEntity.ok(vendorRepository.save(v));
    }

    // ── Purchase Orders ───────────────────────────────────────────────────

    /** Create a PO. sendNow=true marks it SENT (dispatched to vendor). */
    @PostMapping("/purchase-orders")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> createPurchaseOrder(@RequestBody Map<String, Object> body) {
        Long restaurantId = requireTenant();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawLines = (List<Map<String, Object>>) body.get("items");
            List<com.khanabook.saas.dto.PurchaseOrderDtos.PoLine> lines = new java.util.ArrayList<>();
            if (rawLines != null) {
                for (Map<String, Object> l : rawLines) {
                    lines.add(new com.khanabook.saas.dto.PurchaseOrderDtos.PoLine(
                            longOrNull(l.get("rawMaterialId")), decOrNull(l.get("quantity"))));
                }
            }
            return ResponseEntity.ok(inventoryService.createPurchaseOrder(restaurantId,
                    longOrNull(body.get("vendorId")), str(body.get("note")),
                    lines, Boolean.TRUE.equals(body.get("sendNow"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<?> listPurchaseOrders() {
        Long restaurantId = requireTenant();
        return ResponseEntity.ok(purchaseOrderRepository
                .findByRestaurantIdOrderByCreatedAtDesc(restaurantId));
    }

    /** Receive: every PO line becomes stock-in with PURCHASE ledger rows. */
    @PostMapping("/purchase-orders/{id}/receive")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> receivePurchaseOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long restaurantId = requireTenant();
        try {
            java.math.BigDecimal unitCost = body == null ? null : decOrNull(body.get("unitCost"));
            return ResponseEntity.ok(inventoryService.receivePurchaseOrder(
                    restaurantId, id, unitCost));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<?> cancelPurchaseOrder(@PathVariable Long id) {
        Long restaurantId = requireTenant();
        try {
            inventoryService.cancelPurchaseOrder(restaurantId, id);
            return ResponseEntity.ok(Map.of("status", "CANCELLED"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Variance report ───────────────────────────────────────────────────

    /**
     * Unexplained-adjustment report (pilferage/theft signal), sorted worst-first.
     * variancePct > 5% warrants investigation.
     */
    @GetMapping("/variance")
    public ResponseEntity<?> varianceReport(@RequestParam String from, @RequestParam String to) {
        Long restaurantId = requireTenant();
        long fromMs = java.time.LocalDate.parse(from)
                .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        long toMs = java.time.LocalDate.parse(to).plusDays(1)
                .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        return ResponseEntity.ok(inventoryService.varianceReport(restaurantId, fromMs, toMs));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Long requireTenant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "No restaurant context");
        }
        return restaurantId;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    private static String strOr(Object o, String fallback) {
        return o instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static java.math.BigDecimal decOr(Object o, java.math.BigDecimal fallback) {
        java.math.BigDecimal v = decOrNull(o);
        return v != null ? v : fallback;
    }

    private static java.math.BigDecimal decOrNull(Object o) {
        try {
            return o == null ? null : new java.math.BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOrNull(Object o) {
        try {
            return o == null ? null : Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
