package com.khanabook.saas.controller;

import com.khanabook.saas.sync.dto.payload.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
		boolean sharedDataCrossDevice = ignoreDeviceId || firstSync;
		boolean transactionalCrossDevice = ignoreDeviceId || firstSync;

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);

		boolean hasMore = false;

		// ── Helper: truncate to limit, returns a mutable list ─────────────────
		// Type-safe wrapper avoids generic inference issues with wildcard lambdas.

		List<RestaurantProfileDTO> profiles = truncate(
				SyncMapper.<RestaurantProfile, RestaurantProfileDTO>mapList(
						restaurantProfileService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice),
						RestaurantProfileDTO.class),
				limit);
		hasMore = hasMore || profiles.size() > limit;
		response.setProfiles(profiles);

		List<UserDTO> users = truncate(
				SyncMapper.<User, UserDTO>mapList(
						userService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice),
						UserDTO.class),
				limit);
		hasMore = hasMore || users.size() > limit;
		response.setUsers(users);

		List<CategoryDTO> categories = truncate(
				SyncMapper.<Category, CategoryDTO>mapList(
						categoryService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice),
						CategoryDTO.class),
				limit);
		hasMore = hasMore || categories.size() > limit;
		response.setCategories(categories);

		List<MenuItemDTO> menuItems = truncate(
				SyncMapper.<MenuItem, MenuItemDTO>mapList(
						menuItemService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice),
						MenuItemDTO.class),
				limit);
		hasMore = hasMore || menuItems.size() > limit;
		response.setMenuItems(menuItems);

		List<ItemVariantDTO> itemVariants = truncate(
				SyncMapper.<ItemVariant, ItemVariantDTO>mapList(
						itemVariantService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice),
						ItemVariantDTO.class),
				limit);
		hasMore = hasMore || itemVariants.size() > limit;
		response.setItemVariants(itemVariants);

		List<StockLogDTO> stockLogs = truncate(
				SyncMapper.<StockLog, StockLogDTO>mapList(
						stockLogService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice),
						StockLogDTO.class),
				limit);
		hasMore = hasMore || stockLogs.size() > limit;
		response.setStockLogs(stockLogs);

		List<BillDTO> bills = truncate(
				SyncMapper.<Bill, BillDTO>mapList(
						billService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice),
						BillDTO.class),
				limit);
		hasMore = hasMore || bills.size() > limit;
		response.setBills(bills);

		List<BillItemDTO> billItems = truncate(
				SyncMapper.<BillItem, BillItemDTO>mapList(
						billItemService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice),
						BillItemDTO.class),
				limit);
		hasMore = hasMore || billItems.size() > limit;
		response.setBillItems(billItems);

		List<BillPaymentDTO> billPayments = truncate(
				SyncMapper.<BillPayment, BillPaymentDTO>mapList(
						billPaymentService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice),
						BillPaymentDTO.class),
				limit);
		hasMore = hasMore || billPayments.size() > limit;
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
}
