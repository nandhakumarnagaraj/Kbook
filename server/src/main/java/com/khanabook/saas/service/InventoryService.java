package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.entity.StockMovement;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import com.khanabook.saas.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recipe-based raw-material inventory. When a bill reaches a finalized state
 * (completed/paid), the configured recipes for its menu items are used to
 * deduct raw-material stock once per bill (idempotent via
 * {@code bills.inventory_deducted}). Crossing a material's low-stock
 * threshold fires a single push notification to the restaurant.
 */
@Service
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	private final RawMaterialRepository rawMaterialRepository;
	private final ItemRecipeRepository itemRecipeRepository;
	private final BillItemRepository billItemRepository;
	private final PushNotificationService pushNotificationService;
	private final StockMovementRepository stockMovementRepository;
	private final MenuItemRepository menuItemRepository;
	private final com.khanabook.saas.repository.PurchaseOrderRepository poRepository;

	public InventoryService(RawMaterialRepository rawMaterialRepository,
							ItemRecipeRepository itemRecipeRepository,
							BillItemRepository billItemRepository,
							PushNotificationService pushNotificationService,
							StockMovementRepository stockMovementRepository,
							MenuItemRepository menuItemRepository,
							com.khanabook.saas.repository.PurchaseOrderRepository poRepository) {
		this.rawMaterialRepository = rawMaterialRepository;
		this.itemRecipeRepository = itemRecipeRepository;
		this.billItemRepository = billItemRepository;
		this.pushNotificationService = pushNotificationService;
		this.stockMovementRepository = stockMovementRepository;
		this.menuItemRepository = menuItemRepository;
		this.poRepository = poRepository;
	}

	/**
	 * Deducts raw materials for a finalized bill. Safe to call multiple times:
	 * the {@code bills.inventory_deducted} flag makes it a no-op after the first run.
	 */
	@Transactional
	public void deductForFinalizedBill(Bill bill) {
		if (bill == null || Boolean.TRUE.equals(bill.getInventoryDeducted())) {
			return;
		}
		Long tenantId = bill.getRestaurantId();
		if (tenantId == null || bill.getId() == null) {
			return;
		}

		List<BillItem> items = billItemRepository.findByServerBillIdAndIsDeletedFalseOrderById(bill.getId());
		if (items.isEmpty()) {
			bill.setInventoryDeducted(true);
			return;
		}

		Map<Long, BigDecimal> deductions = new HashMap<>();
		for (BillItem item : items) {
			Long menuItemId = item.getServerMenuItemId() != null ? item.getServerMenuItemId() : item.getMenuItemId();
			if (menuItemId == null || item.getQuantity() == null) {
				continue;
			}
			List<ItemRecipe> recipes =
					itemRecipeRepository.findByRestaurantIdAndMenuItemIdAndIsDeletedFalse(tenantId, menuItemId);
			for (ItemRecipe recipe : recipes) {
				BigDecimal qty = recipe.getQuantityPerItem()
						.multiply(BigDecimal.valueOf(item.getQuantity()));
				deductions.merge(recipe.getRawMaterial().getId(), qty, BigDecimal::add);
			}
		}

		for (Map.Entry<Long, BigDecimal> entry : deductions.entrySet()) {
			rawMaterialRepository.findById(entry.getKey()).ifPresent(material -> {
				boolean wasAboveThreshold = !isAtOrBelowThreshold(material);
				boolean wasInStock = material.getStockQuantity().signum() > 0;
				material.setStockQuantity(material.getStockQuantity().subtract(entry.getValue()));
				material.setUpdatedAt(System.currentTimeMillis());
				rawMaterialRepository.save(material);

				recordMovement(tenantId, material, StockMovement.KIND_SALES_DEDUCT,
						entry.getValue().negate(), null, null,
						"Bill #" + bill.getId(), bill.getId(), null);
				billItemRepository.flush();

				if (wasAboveThreshold && isAtOrBelowThreshold(material)) {
					try {
						pushNotificationService.pushToRestaurant(
								tenantId,
								"Low Stock Alert",
								material.getName() + " is running low (" +
										material.getStockQuantity().toPlainString() + " " + material.getUnit() + ")",
								"inventory_low",
								String.valueOf(material.getId()),
								"raw_material",
								null);
					} catch (Exception e) {
						log.warn("Failed to push low-stock alert: {}", e.getMessage());
					}
				}

				if (wasInStock && material.getStockQuantity().signum() <= 0) {
					cascadeOutOfStock(tenantId, material);
				}
			});
		}

		bill.setInventoryDeducted(true);
		log.info("Inventory deducted for billId={} restaurantId={} materials={}",
				bill.getId(), tenantId, deductions.size());
	}

	/**
	 * When a raw material is exhausted, every menu item whose recipe needs it
	 * becomes unavailable everywhere it is sold (POS sync + customer QR page).
	 */
	private void cascadeOutOfStock(Long tenantId, RawMaterial material) {
		try {
			List<ItemRecipe> recipes = itemRecipeRepository
					.findByRestaurantIdAndRawMaterial(tenantId, material.getId());
			int hidden = 0;
			for (ItemRecipe recipe : recipes) {
				menuItemRepository.findById(recipe.getMenuItemId())
						.filter(mi -> mi.getRestaurantId().equals(tenantId)
								&& Boolean.TRUE.equals(mi.getIsAvailable()))
						.ifPresent(mi -> {
							mi.setIsAvailable(false);
							mi.setUpdatedAt(System.currentTimeMillis());
							menuItemRepository.save(mi);
						});
				hidden++;
			}
			pushNotificationService.pushToRestaurant(
					tenantId,
					"Out of Stock",
					material.getName() + " finished. " + hidden
							+ " menu item(s) hidden automatically.",
					"inventory_low",
					String.valueOf(material.getId()),
					"raw_material",
					null);
			log.info("Zero-stock cascade: restaurantId={} material={} hid {} items",
					tenantId, material.getName(), hidden);
		} catch (Exception e) {
			log.warn("Zero-stock cascade failed for {}: {}", material.getName(), e.getMessage());
		}
	}

	// ── Manual stock loop: purchase / wastage / physical count ────────────

	/** Stock-in with weighted-average cost update + optional new expiry date. */
	@Transactional
	public RawMaterial purchase(Long restaurantId, Long materialId, BigDecimal quantity,
								BigDecimal unitCost, Long vendorId, Long expiryDate) {
		RawMaterial material = rawMaterialRepository.findById(materialId)
				.filter(m -> m.getRestaurantId().equals(restaurantId))
				.orElseThrow(() -> new IllegalArgumentException("Material not found"));
		BigDecimal oldQty = material.getStockQuantity();
		BigDecimal oldAvg = material.getCostPerUnit() != null ? material.getCostPerUnit() : BigDecimal.ZERO;

		if (unitCost != null && oldQty.signum() > 0) {
			BigDecimal totalValue = oldAvg.multiply(oldQty).add(unitCost.multiply(quantity));
			material.setCostPerUnit(totalValue.divide(oldQty.add(quantity), 2, java.math.RoundingMode.HALF_UP));
		} else if (unitCost != null) {
			material.setCostPerUnit(unitCost);
		}
		material.setStockQuantity(oldQty.add(quantity));
		if (expiryDate != null) material.setExpiryDate(expiryDate);
		material.setUpdatedAt(System.currentTimeMillis());
		RawMaterial saved = rawMaterialRepository.save(material);

		recordMovement(restaurantId, saved, StockMovement.KIND_PURCHASE,
				quantity, unitCost, vendorId, null, null, null);
		return saved;
	}

	/** Wastage with a mandatory reason; fires zero-stock cascade if exhausted. */
	@Transactional
	public RawMaterial wastage(Long restaurantId, Long materialId, BigDecimal quantity, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Wastage reason is required");
		}
		RawMaterial material = rawMaterialRepository.findById(materialId)
				.filter(m -> m.getRestaurantId().equals(restaurantId))
				.orElseThrow(() -> new IllegalArgumentException("Material not found"));
		boolean wasAboveThreshold = !isAtOrBelowThreshold(material);
		boolean wasInStock = material.getStockQuantity().signum() > 0;

		material.setStockQuantity(material.getStockQuantity().subtract(quantity));
		material.setUpdatedAt(System.currentTimeMillis());
		RawMaterial saved = rawMaterialRepository.save(material);

		recordMovement(restaurantId, saved, StockMovement.KIND_WASTAGE,
				quantity.negate(), null, null, reason, null, null);

		if (wasAboveThreshold && isAtOrBelowThreshold(saved)) {
			try {
				pushNotificationService.pushToRestaurant(restaurantId, "Low Stock Alert",
						saved.getName() + " running low after wastage ("
								+ saved.getStockQuantity().toPlainString() + " " + saved.getUnit() + ")",
						"inventory_low", String.valueOf(saved.getId()), "raw_material", null);
			} catch (Exception e) {
				log.warn("Low-stock push failed: {}", e.getMessage());
			}
		}
		if (wasInStock && saved.getStockQuantity().signum() <= 0) {
			cascadeOutOfStock(restaurantId, saved);
		}
		return saved;
	}

	/**
	 * Evening physical count reconciliation. Returns the variance
	 * (counted - system). Positive variance = found extra, negative = missing.
	 */
	@Transactional
	public Map<String, Object> adjustPhysicalCount(Long restaurantId, Long materialId,
												   BigDecimal countedQty, Long userId) {
		RawMaterial material = rawMaterialRepository.findById(materialId)
				.filter(m -> m.getRestaurantId().equals(restaurantId))
				.orElseThrow(() -> new IllegalArgumentException("Material not found"));
		BigDecimal systemQty = material.getStockQuantity();
		BigDecimal variance = countedQty.subtract(systemQty);
		material.setStockQuantity(countedQty);
		material.setUpdatedAt(System.currentTimeMillis());
		rawMaterialRepository.save(material);

		recordMovement(restaurantId, material, StockMovement.KIND_ADJUST, variance,
				null, null, "Physical count reconciliation", null, userId);

		Map<String, Object> result = new HashMap<>();
		result.put("materialId", materialId);
		result.put("name", material.getName());
		result.put("systemQty", systemQty);
		result.put("countedQty", countedQty);
		result.put("variance", variance);
		return result;
	}

	private void recordMovement(Long restaurantId, RawMaterial material, String kind,
								BigDecimal quantity, BigDecimal unitCost, Long vendorId,
								String reason, Long billId, Long userId) {
		StockMovement mv = new StockMovement();
		mv.setRestaurantId(restaurantId);
		mv.setRawMaterial(material);
		mv.setKind(kind);
		mv.setQuantity(quantity);
		mv.setUnitCost(unitCost);
		mv.setVendorId(vendorId);
		mv.setReason(reason);
		mv.setBillId(billId);
		mv.setCreatedByUserId(userId);
		mv.setCreatedAt(System.currentTimeMillis());
		stockMovementRepository.save(mv);
	}

	private boolean isAtOrBelowThreshold(RawMaterial material) {
		return material.getStockQuantity()
				.compareTo(material.getLowStockThreshold()) <= 0;
	}

    // ── Purchase Orders ───────────────────────────────────────────────────

    /**
     * Creates a PO. If status SENT it is considered dispatched to the vendor.
     */
    @Transactional
    public com.khanabook.saas.entity.PurchaseOrder createPurchaseOrder(
            Long restaurantId, Long vendorId, String note,
            List<com.khanabook.saas.dto.PurchaseOrderDtos.PoLine> lines, boolean sendNow) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one line is required");
        }
        var po = new com.khanabook.saas.entity.PurchaseOrder();
        po.setRestaurantId(restaurantId);
        po.setVendorId(vendorId);
        po.setNote(note);
        long now = System.currentTimeMillis();
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        po.setStatus(sendNow ? com.khanabook.saas.entity.PurchaseOrder.STATUS_SENT
                : com.khanabook.saas.entity.PurchaseOrder.STATUS_DRAFT);
        for (var line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Line quantities must be positive");
            }
            RawMaterial material = rawMaterialRepository.findById(line.rawMaterialId())
                    .filter(m -> m.getRestaurantId().equals(restaurantId))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Material not found: " + line.rawMaterialId()));
            var item = new com.khanabook.saas.entity.PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setRawMaterial(material);
            item.setQuantity(line.quantity());
            po.getItems().add(item);
        }
        return poRepository.save(po);
    }

    /** Marks a PO received: every line becomes a PURCHASE (stock-in). */
    @Transactional
    public List<RawMaterial> receivePurchaseOrder(Long restaurantId, Long poId,
                                                  java.math.BigDecimal unitCost) {
        var po = poRepository.findById(poId)
                .filter(p -> p.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));
        if (!com.khanabook.saas.entity.PurchaseOrder.STATUS_SENT.equals(po.getStatus())
                && !com.khanabook.saas.entity.PurchaseOrder.STATUS_DRAFT.equals(po.getStatus())) {
            throw new IllegalStateException("Only SENT/DRAFT POs can be received");
        }
        List<RawMaterial> updated = new ArrayList<>();
        for (var item : po.getItems()) {
            updated.add(purchase(restaurantId, item.getRawMaterial().getId(),
                    item.getQuantity(), unitCost, po.getVendorId(), null));
        }
        po.setStatus(com.khanabook.saas.entity.PurchaseOrder.STATUS_RECEIVED);
        po.setUpdatedAt(System.currentTimeMillis());
        poRepository.save(po);
        return updated;
    }

    @Transactional
    public void cancelPurchaseOrder(Long restaurantId, Long poId) {
        var po = poRepository.findById(poId)
                .filter(p -> p.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));
        if (com.khanabook.saas.entity.PurchaseOrder.STATUS_RECEIVED.equals(po.getStatus())) {
            throw new IllegalStateException("Received POs cannot be cancelled");
        }
        po.setStatus(com.khanabook.saas.entity.PurchaseOrder.STATUS_CANCELLED);
        po.setUpdatedAt(System.currentTimeMillis());
        poRepository.save(po);
    }

    // ── Variance report ───────────────────────────────────────────────────

    /**
     * Per-material consumption variance for a window: compares ledger kinds.
     * adjustedQty is the unexplained gap (physical-count corrections) —
     * POSist-style pilferage signal. variancePct = |adjusted| / consumed * 100.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> varianceReport(Long restaurantId, Long from, Long to) {
        Map<Long, Map<String, BigDecimal>> byKind = new HashMap<>();
        for (StockMovement mv : stockMovementRepository.findByRestaurantIdAndCreatedAtBetween(
                restaurantId, from, to)) {
            Long mid = mv.getRawMaterial().getId();
            if (StockMovement.KIND_SALES_DEDUCT.equals(mv.getKind())
                    || StockMovement.KIND_WASTAGE.equals(mv.getKind())) {
                byKind.computeIfAbsent(mid, k -> new HashMap<>())
                        .merge("consumed", mv.getQuantity().abs(), BigDecimal::add);
            } else if (StockMovement.KIND_ADJUST.equals(mv.getKind())) {
                byKind.computeIfAbsent(mid, k -> new HashMap<>())
                        .merge("adjusted", mv.getQuantity().abs(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : byKind.entrySet()) {
            RawMaterial material = rawMaterialRepository.findById(entry.getKey()).orElse(null);
            if (material == null) continue;
            BigDecimal consumed = entry.getValue().getOrDefault("consumed", BigDecimal.ZERO);
            BigDecimal adjusted = entry.getValue().getOrDefault("adjusted", BigDecimal.ZERO);
            BigDecimal denominator = consumed.signum() > 0 ? consumed : BigDecimal.ONE;
            BigDecimal pct = adjusted.divide(denominator, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            Map<String, Object> row = new HashMap<>();
            row.put("materialId", material.getId());
            row.put("name", material.getName());
            row.put("unit", material.getUnit());
            row.put("consumed", consumed);
            row.put("unexplainedAdjustment", adjusted);
            row.put("variancePct", pct);
            out.add(row);
        }
        out.sort((a, b) -> ((BigDecimal) b.get("variancePct"))
                .compareTo((BigDecimal) a.get("variancePct")));
        return out;
    }
}