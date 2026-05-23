package com.khanabook.saas.controller;

import com.khanabook.saas.sync.dto.payload.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.StockLog;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sync/master")
@RequiredArgsConstructor
public class MasterSyncController {

	private static final Logger log = LoggerFactory.getLogger(MasterSyncController.class);

	private final RestaurantProfileService restaurantProfileService;
	private final UserService userService;
	private final CategoryService categoryService;
	private final MenuItemService menuItemService;
	private final ItemVariantService itemVariantService;
	private final StockLogService stockLogService;
	private final BillService billService;
	private final BillItemService billItemService;
	private final BillPaymentService billPaymentService;
	private final EasebuzzSubMerchantRepository subMerchantRepo;
	private final RestaurantProfileRepository profileRepo;
	private final SubMerchantService subMerchantService;

	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	@GetMapping("/pull")
	public ResponseEntity<MasterSyncResponseDTO> pullMasterSync(@RequestParam Long lastSyncTimestamp,
			@RequestParam String deviceId, @RequestParam(required = false) Long restaurantId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId,
			@RequestParam(defaultValue = "10000") int limit,
			@RequestParam(defaultValue = "0") int offset,
			HttpServletRequest request) {

		Long tenantId = TenantContext.getCurrentTenant();
		String role = TenantContext.getCurrentRole();

		if ("KBOOK_ADMIN".equals(role) && restaurantId != null) {
			String adminUsername = org.springframework.security.core.context.SecurityContextHolder
					.getContext().getAuthentication().getName();
			log.warn("ADMIN_TENANT_OVERRIDE admin={} impersonating tenantId={} ip={}",
					adminUsername, restaurantId, request.getRemoteAddr());
			tenantId = restaurantId;
		}

		long currentServerTime = System.currentTimeMillis();
		boolean firstSync = lastSyncTimestamp == null || lastSyncTimestamp == 0;

		// Retroactive auto-enable: if a sub-merchant with an Easebuzz ID exists but
		// easebuzzEnabled is still false, fix it. This handles sub-merchants created
		// before the auto-enable feature was added.
		if (firstSync) {
			autoEnableEasebuzzForExistingSubMerchants(tenantId);
			autoEnableMarketplaceIfConfigured(tenantId);
		}
		boolean sharedDataCrossDevice = ignoreDeviceId || firstSync;
		boolean transactionalCrossDevice = ignoreDeviceId || firstSync;

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);

		boolean hasMore = false;

		// ── Helper: truncate to limit, returns a mutable list ─────────────────
		// Type-safe wrapper avoids generic inference issues with wildcard lambdas.

		List<RestaurantProfile> rawProfiles = restaurantProfileService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice);
		hasMore = hasMore || rawProfiles.size() > limit;
		List<RestaurantProfileDTO> profiles = truncate(
				SyncMapper.<RestaurantProfile, RestaurantProfileDTO>mapList(rawProfiles, RestaurantProfileDTO.class),
				limit);
		response.setProfiles(profiles);

		List<User> rawUsers = userService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice);
		hasMore = hasMore || rawUsers.size() > limit;
		List<UserDTO> users = truncate(
				SyncMapper.<User, UserDTO>mapList(rawUsers, UserDTO.class), limit);
		response.setUsers(users);

		List<Category> rawCategories = categoryService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice);
		hasMore = hasMore || rawCategories.size() > limit;
		List<CategoryDTO> categories = truncate(
				SyncMapper.<Category, CategoryDTO>mapList(rawCategories, CategoryDTO.class), limit);
		response.setCategories(categories);

		List<MenuItem> rawMenuItems = menuItemService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice);
		hasMore = hasMore || rawMenuItems.size() > limit;
		List<MenuItemDTO> menuItems = truncate(
				SyncMapper.<MenuItem, MenuItemDTO>mapList(rawMenuItems, MenuItemDTO.class), limit);
		response.setMenuItems(menuItems);

		List<ItemVariant> rawItemVariants = itemVariantService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice);
		hasMore = hasMore || rawItemVariants.size() > limit;
		List<ItemVariantDTO> itemVariants = truncate(
				SyncMapper.<ItemVariant, ItemVariantDTO>mapList(rawItemVariants, ItemVariantDTO.class), limit);
		response.setItemVariants(itemVariants);

		List<StockLog> rawStockLogs = stockLogService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice);
		hasMore = hasMore || rawStockLogs.size() > limit;
		List<StockLogDTO> stockLogs = truncate(
				SyncMapper.<StockLog, StockLogDTO>mapList(rawStockLogs, StockLogDTO.class), limit);
		response.setStockLogs(stockLogs);

		List<Bill> rawBills = billService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice);
		hasMore = hasMore || rawBills.size() > limit;
		List<BillDTO> bills = truncate(
				SyncMapper.<Bill, BillDTO>mapList(rawBills, BillDTO.class), limit);
		response.setBills(bills);

		List<BillItem> rawBillItems = billItemService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice);
		hasMore = hasMore || rawBillItems.size() > limit;
		List<BillItemDTO> billItems = truncate(
				SyncMapper.<BillItem, BillItemDTO>mapList(rawBillItems, BillItemDTO.class), limit);
		response.setBillItems(billItems);

		List<BillPayment> rawBillPayments = billPaymentService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice);
		hasMore = hasMore || rawBillPayments.size() > limit;
		List<BillPaymentDTO> billPayments = truncate(
				SyncMapper.<BillPayment, BillPaymentDTO>mapList(rawBillPayments, BillPaymentDTO.class), limit);
		response.setBillPayments(billPayments);

		response.setHasMore(hasMore);

		int profilesCount = response.getProfiles() == null ? 0 : response.getProfiles().size();
		int usersCount = response.getUsers() == null ? 0 : response.getUsers().size();
		int categoriesCount = response.getCategories() == null ? 0 : response.getCategories().size();
		int menuItemsCount = response.getMenuItems() == null ? 0 : response.getMenuItems().size();
		int itemVariantsCount = response.getItemVariants() == null ? 0 : response.getItemVariants().size();
		int stockLogsCount = response.getStockLogs() == null ? 0 : response.getStockLogs().size();
		int billsCount = response.getBills() == null ? 0 : response.getBills().size();
		int billItemsCount = response.getBillItems() == null ? 0 : response.getBillItems().size();
		int billPaymentsCount = response.getBillPayments() == null ? 0 : response.getBillPayments().size();

		log.info("Master sync pull tenantId={} deviceId={} firstSync={} explicitIgnoreDeviceId={} sharedDataCrossDevice={} transactionalCrossDevice={} " +
				"profiles={} users={} categories={} menuItems={} variants={} stockLogs={} bills={} billItems={} billPayments={} " +
				"limit={} offset={} hasMore={}",
				tenantId, deviceId, firstSync, ignoreDeviceId, sharedDataCrossDevice, transactionalCrossDevice,
				profilesCount, usersCount, categoriesCount,
				menuItemsCount, itemVariantsCount, stockLogsCount, billsCount, billItemsCount, billPaymentsCount,
				limit, offset, hasMore);

		return ResponseEntity.ok(response);
	}

	/**
	 * Returns a new mutable list containing at most {@code limit} elements from the source.
	 * Prevents unbounded response sizes for tenants with large datasets.
	 */
	private static <T> List<T> truncate(List<T> source, int limit) {
		if (source == null || source.isEmpty()) return new java.util.ArrayList<>();
		return new java.util.ArrayList<>(source.size() <= limit ? source : source.subList(0, limit));
	}

	/**
	 * Retroactively enables easebuzzEnabled for restaurants that have a sub-merchant
	 * with a non-blank Easebuzz ID but whose profile still has easebuzzEnabled = false/null.
	 * Uses REQUIRES_NEW to write outside the read-only pull transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	private void autoEnableEasebuzzForExistingSubMerchants(Long restaurantId) {
		var smOpt = subMerchantRepo.findByRestaurantId(restaurantId);
		boolean hasValidSubMerchant = smOpt.isPresent()
				&& smOpt.get().getSubMerchantId() != null
				&& !smOpt.get().getSubMerchantId().isBlank();
		if (hasValidSubMerchant) {
			subMerchantService.ensureEasebuzzEnabled(restaurantId);
			log.info("Auto-enabled easebuzz for restaurant {} (has existing sub-merchant with ID)", restaurantId);
		}
	}

	/**
	 * Retroactively enables Zomato/Swiggy if their API keys are configured
	 * but the enabled flag is still false/null.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	private void autoEnableMarketplaceIfConfigured(Long restaurantId) {
		profileRepo.findByRestaurantId(restaurantId).ifPresent(profile -> {
			boolean changed = false;
			// Zomato: auto-enable if apiKey + outletId are set
			if (profile.getZomatoApiKey() != null && !profile.getZomatoApiKey().isBlank()
					&& profile.getZomatoOutletId() != null && !profile.getZomatoOutletId().isBlank()
					&& (profile.getZomatoEnabled() == null || !profile.getZomatoEnabled())) {
				profile.setZomatoEnabled(true);
				changed = true;
				log.info("Auto-enabled zomato for restaurant {} (apiKey+outletId configured)", restaurantId);
			}
			// Swiggy: auto-enable if apiKey + storeId are set
			if (profile.getSwiggyApiKey() != null && !profile.getSwiggyApiKey().isBlank()
					&& profile.getSwiggyStoreId() != null && !profile.getSwiggyStoreId().isBlank()
					&& (profile.getSwiggyEnabled() == null || !profile.getSwiggyEnabled())) {
				profile.setSwiggyEnabled(true);
				changed = true;
				log.info("Auto-enabled swiggy for restaurant {} (apiKey+storeId configured)", restaurantId);
			}
			if (changed) {
				long now = System.currentTimeMillis();
				profile.setUpdatedAt(now);
				profile.setServerUpdatedAt(now);
				profile.setDeviceId("server");
				profileRepo.save(profile);
			}
		});
	}
}
