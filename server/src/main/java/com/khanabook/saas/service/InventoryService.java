package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

	public InventoryService(RawMaterialRepository rawMaterialRepository,
							ItemRecipeRepository itemRecipeRepository,
							BillItemRepository billItemRepository,
							PushNotificationService pushNotificationService) {
		this.rawMaterialRepository = rawMaterialRepository;
		this.itemRecipeRepository = itemRecipeRepository;
		this.billItemRepository = billItemRepository;
		this.pushNotificationService = pushNotificationService;
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
				material.setStockQuantity(material.getStockQuantity().subtract(entry.getValue()));
				material.setUpdatedAt(System.currentTimeMillis());
				rawMaterialRepository.save(material);

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
			});
		}

		bill.setInventoryDeducted(true);
		log.info("Inventory deducted for billId={} restaurantId={} materials={}",
				bill.getId(), tenantId, deductions.size());
	}

	private boolean isAtOrBelowThreshold(RawMaterial material) {
		return material.getStockQuantity()
				.compareTo(material.getLowStockThreshold()) <= 0;
	}
}
