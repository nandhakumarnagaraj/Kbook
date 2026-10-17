package com.khanabook.saas.feature.sync.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;





import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillDTO;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillItemDTO;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillPaymentDTO;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.StockLogDTO;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryDTO;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemVariantDTO;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemDTO;
import com.khanabook.saas.feature.sync.data.BaseSyncEntity;

/**
 * Guards the whole class of silent sync data loss.
 *
 * <p>SyncMapper maps every push with the two-arg BeanUtils.copyProperties(dto, entity), which
 * matches by property name and skips anything it cannot match — with no error. On top of that
 * Spring disables Jackson's FAIL_ON_UNKNOWN_PROPERTIES, so a DTO that lacks a field simply
 * discards the incoming value at the HTTP boundary. Both failures are silent: the request
 * succeeds, the field is written as null, and the only symptom is a value quietly reverting in
 * production days later. That is how MenuItemDTO lost currentStock/foodType/barcode/
 * lowStockThreshold, and how StockLogDTO's "changeAmount" left the NOT NULL delta column unset
 * so that no stock-log row could ever be written.
 *
 * <p>This test makes the drift a build failure instead. Any entity field that the device is
 * allowed to own must exist on the DTO with a matching name and type. Any entity field the
 * server owns must instead be listed in the server-owned allow-list below, because those are
 * restored from the existing row by preserveServerOwnedState and must never be accepted from a
 * client push.
 */
class SyncDtoEntityFieldParityTest {

	/**
	 * Entity fields a client push must never set, because the server is the only writer and
	 * GenericSyncService restores them from the existing row. Adding a new one here without
	 * actually restoring it in preserveServerOwnedState would hide a real bug, so each entry
	 * names the reason it is server-owned.
	 */
	private static final Set<String> SERVER_OWNED = Set.of(
			// Set by the gateway webhook / settlement, never by the till.
			"gatewayTxnId", "gatewayStatus", "refundId",
			"settledAmount", "settledAt", "commissionAmount",
			// Set by InventoryService.deductForFinalizedBill. If a push reset this to false
			// the bill would be deducted a second time.
			"inventoryDeducted",
			// Set by ItemVariantRepository.recalculateStock from the stock-log ledger.
			"currentStock", "lowStockThreshold",
			// Marketplace integration credentials, configured by an owner in web admin.
			"zomatoEnabled", "swiggyEnabled", "zomatoOutletId", "swiggyStoreId",
			"zomatoApiKey", "zomatoWebhookSecret", "swiggyApiKey", "swiggyWebhookSecret",
			"marketplaceNotes", "ownWebsiteEnabled",
			// Auth state owned by the identity service, not the device.
			"loginId", "authProvider", "passwordHash", "googleEmail",
			"phoneNumber", "pinHash", "pinSetAt",
			// Optimistic-locking and terminal identity, assigned by the sync layer itself.
			"version", "terminalId",
			// JPA association views maintained through their *_id columns.
			"menuItem", "bill", "itemVariant", "category",
			// Restored by preserveServerOwnedState: isSuspended, role, tokenInvalidatedAt.
			"isSuspended", "role", "tokenInvalidatedAt",
			// String on the DTO, LocalDate on the entity, so BeanUtils skips them. SyncMapper
			// converts and mergeCounterState restores these explicitly instead.
			"lastResetDateProper", "fssaiExpiryDate", "gstExpiryDate"
	);

	/**
	 * DTO properties with no entity counterpart. BeanUtils ignores them, so they are dead
	 * weight that reads like it is being persisted. Kept explicit so a new one is deliberate.
	 */
	private static final Set<String> KNOWN_UNMAPPED_DTO_FIELDS = Set.of(
			// CategoryDTO/MenuItemDTO copied isVeg from each other; is_veg lives on categories
			// only, and menuitems has no sort_order or category_local_id column at all.
			"isVeg", "sortOrder", "categoryLocalId",
			// Client-side convenience references resolved by the push service, not columns.
			"itemId", "itemLocalId", "billLocalId", "variantLocalId",
			// lastResetDate is a String here and a LocalDate on the entity; the mapper and
			// mergeCounterState handle it explicitly because BeanUtils cannot.
			"lastResetDate", "fssaiExpiryDate", "gstExpiryDate",
			// Client conveniences the server derives or ignores: the wire calls on-hand
			// quantity "stock" while the column is current_stock, which the ledger owns.
			"stock", "trackStock",
			// Accepted for forward compatibility; no column backs these today.
			"paidAt", "status"
	);

	@Test
	@DisplayName("every client-owned entity field is present on its push DTO with a matching name and type")
	void clientOwnedFieldsAreMapped() {
		record Pair(Class<?> dto, Class<?> entity) {}
		List<Pair> pairs = List.of(
				new Pair(MenuItemDTO.class, MenuItem.class),
				new Pair(ItemVariantDTO.class, ItemVariant.class),
				new Pair(CategoryDTO.class, Category.class),
				new Pair(BillDTO.class, Bill.class),
				new Pair(BillItemDTO.class, BillItem.class),
				new Pair(BillPaymentDTO.class, BillPayment.class),
				new Pair(StockLogDTO.class, StockLog.class),
				new Pair(com.khanabook.saas.feature.auth.data.UserDTO.class, com.khanabook.saas.feature.auth.entity.User.class),
				new Pair(com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO.class, com.khanabook.saas.feature.restaurants.data.RestaurantProfile.class));

		Set<String> unexplained = new TreeSet<>();
		for (Pair pair : pairs) {
			Set<String> dtoTypes = propertyTypes(pair.dto());
			Set<String> entityTypes = entityPropertyNames(pair.entity());

			for (String entityField : entityTypes) {
				if (SERVER_OWNED.contains(entityField)) {
					continue;
				}
				if (!dtoTypes.contains(entityField)) {
					unexplained.add(pair.entity().getSimpleName() + "." + entityField
							+ " is absent from " + pair.dto().getSimpleName()
							+ " (BeanUtils would silently drop it)");
					continue;
				}
				String dtoType = declaredType(pair.dto(), entityField);
				String entityType = declaredType(pair.entity(), entityField);
				if (!dtoType.equals(entityType)) {
					unexplained.add(pair.entity().getSimpleName() + "." + entityField
							+ " is " + entityType + " on the entity but " + dtoType
							+ " on " + pair.dto().getSimpleName() + " (BeanUtils skips type mismatches)");
				}
			}
		}
		assertEquals(Set.of(), unexplained,
				"Client-owned entity fields are missing from their push DTO. Add the field to the DTO, "
						+ "or, if the server owns it, add it to SERVER_OWNED and make sure "
						+ "preserveServerOwnedState actually restores it.");
	}

	@Test
	@DisplayName("no DTO declares a property that is neither an entity field nor explicitly allow-listed")
	void noPhantomDtoFields() {
		record Pair(Class<?> dto, Class<?> entity) {}
		List<Pair> pairs = List.of(
				new Pair(MenuItemDTO.class, MenuItem.class),
				new Pair(ItemVariantDTO.class, ItemVariant.class),
				new Pair(CategoryDTO.class, Category.class),
				new Pair(BillDTO.class, Bill.class),
				new Pair(BillItemDTO.class, BillItem.class),
				new Pair(BillPaymentDTO.class, BillPayment.class),
				new Pair(StockLogDTO.class, StockLog.class),
				new Pair(com.khanabook.saas.feature.auth.data.UserDTO.class, com.khanabook.saas.feature.auth.entity.User.class),
				new Pair(com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO.class, com.khanabook.saas.feature.restaurants.data.RestaurantProfile.class));

		Set<String> unexplained = new TreeSet<>();
		for (Pair pair : pairs) {
			Set<String> entityTypes = entityPropertyNames(pair.entity());
			for (String dtoField : declaredFieldNames(pair.dto())) {
				if (!entityTypes.contains(dtoField) && !KNOWN_UNMAPPED_DTO_FIELDS.contains(dtoField)) {
					unexplained.add(pair.dto().getSimpleName() + "." + dtoField
							+ " has no entity counterpart and is not allow-listed");
				}
			}
		}
		assertEquals(Set.of(), unexplained,
				"Remove the property or add it to KNOWN_UNMAPPED_DTO_FIELDS if the omission is deliberate.");
	}

	@Test
	@DisplayName("StockLogDTO binds the delta field the entity and the client both use")
	void stockLogDeltaBinds() {
		// Regression: this was named "changeAmount" on the DTO, so nothing ever bound it and
		// the NOT NULL delta column made every stock-log push fail to persist.
		assertTrue(declaredFieldNames(StockLogDTO.class).contains("delta"),
				"StockLogDTO must declare delta so BeanUtils populates StockLog.delta");
		StockLogDTO dto = new StockLogDTO();
		dto.setDelta(new java.math.BigDecimal("-3.0000"));
		StockLog entity = new StockLog();
		org.springframework.beans.BeanUtils.copyProperties(dto, entity);
		assertEquals(0, new java.math.BigDecimal("-3.0000").compareTo(entity.getDelta()),
				"BeanUtils must copy delta onto the entity");
	}

	/** Entity properties including those inherited from {@link BaseSyncEntity}. */
	private static Set<String> entityPropertyNames(Class<?> entity) {
		Set<String> names = new LinkedHashSet<>();
		Class<?> current = entity;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					names.add(field.getName());
				}
			}
			current = current.getSuperclass();
		}
		return names;
	}

	private static Set<String> declaredFieldNames(Class<?> type) {
		return Arrays.stream(type.getDeclaredFields())
				.filter(f -> !Modifier.isStatic(f.getModifiers()))
				.map(Field::getName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Entity fields that are both present on the DTO and type-compatible. */
	private static Set<String> propertyTypes(Class<?> dto) {
		Set<String> names = new LinkedHashSet<>();
		Class<?> current = dto;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					names.add(field.getName());
				}
			}
			current = current.getSuperclass();
		}
		return names;
	}

	private static String declaredType(Class<?> type, String fieldName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			try {
				return current.getDeclaredField(fieldName).getType().getSimpleName();
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			}
		}
		return "MISSING";
	}
}
