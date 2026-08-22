package com.khanabook.saas.controller;

import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Recipe-based raw-material inventory (Phase 3). Tenant-scoped by JWT;
 * management actions are OWNER-only, reads allowed for all staff.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final RawMaterialRepository rawMaterialRepository;
    private final ItemRecipeRepository itemRecipeRepository;

    public InventoryController(RawMaterialRepository rawMaterialRepository,
                               ItemRecipeRepository itemRecipeRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.itemRecipeRepository = itemRecipeRepository;
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
