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
		// First sync should bootstrap shared restaurant configuration and catalog data,
		// but transactional data must stay device-scoped unless the caller explicitly
		// requests a cross-device recovery pull.
		boolean sharedDataCrossDevice = ignoreDeviceId || firstSync;
		// On first sync (re-login after logout), also include own-device bills so they
		// are restored. Without this, bills created before logout are invisible after login
		// because the deviceId filter excludes them.
		boolean transactionalCrossDevice = ignoreDeviceId || firstSync;

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);
		response.setProfiles(SyncMapper.mapList(restaurantProfileService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), RestaurantProfileDTO.class));
		response.setUsers(SyncMapper.mapList(userService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), UserDTO.class));
		response.setCategories(SyncMapper.mapList(categoryService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), CategoryDTO.class));
		response.setMenuItems(SyncMapper.mapList(menuItemService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), MenuItemDTO.class));
		response.setItemVariants(SyncMapper.mapList(itemVariantService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), ItemVariantDTO.class));
		response.setStockLogs(SyncMapper.mapList(stockLogService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice), StockLogDTO.class));
		response.setBills(SyncMapper.mapList(billService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice), BillDTO.class));
		response.setBillItems(SyncMapper.mapList(billItemService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice), BillItemDTO.class));
		response.setBillPayments(SyncMapper.mapList(billPaymentService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice), BillPaymentDTO.class));

		int profilesCount = response.getProfiles() == null ? 0 : response.getProfiles().size();
		int usersCount = response.getUsers() == null ? 0 : response.getUsers().size();
		int categoriesCount = response.getCategories() == null ? 0 : response.getCategories().size();
		int menuItemsCount = response.getMenuItems() == null ? 0 : response.getMenuItems().size();
		int itemVariantsCount = response.getItemVariants() == null ? 0 : response.getItemVariants().size();
		int stockLogsCount = response.getStockLogs() == null ? 0 : response.getStockLogs().size();
		int billsCount = response.getBills() == null ? 0 : response.getBills().size();
		int billItemsCount = response.getBillItems() == null ? 0 : response.getBillItems().size();
		int billPaymentsCount = response.getBillPayments() == null ? 0 : response.getBillPayments().size();

		log.info("Master sync pull tenantId={} deviceId={} firstSync={} explicitIgnoreDeviceId={} sharedDataCrossDevice={} transactionalCrossDevice={} profiles={} users={} categories={} " +
				"menuItems={} variants={} stockLogs={} bills={} billItems={} billPayments={}",
				tenantId, deviceId, firstSync, ignoreDeviceId, sharedDataCrossDevice, transactionalCrossDevice,
				profilesCount, usersCount, categoriesCount,
				menuItemsCount, itemVariantsCount, stockLogsCount, billsCount, billItemsCount, billPaymentsCount);

		return ResponseEntity.ok(response);
	}
}
