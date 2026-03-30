package com.khanabook.saas.controller;

import com.khanabook.saas.debug.DebugNDJSONLogger;
import com.khanabook.saas.sync.dto.payload.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sync/master")
@RequiredArgsConstructor
public class MasterSyncController {

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
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {

		Long tenantId = TenantContext.getCurrentTenant();
		String role = TenantContext.getCurrentRole();

		if ("KBOOK_ADMIN".equals(role) && restaurantId != null) {
			tenantId = restaurantId;
		}

		long currentServerTime = System.currentTimeMillis();
		boolean shouldIgnoreDeviceId = ignoreDeviceId || (lastSyncTimestamp == null || lastSyncTimestamp == 0);

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);
		response.setProfiles(SyncMapper.mapList(restaurantProfileService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), RestaurantProfileDTO.class));
		response.setUsers(SyncMapper.mapList(userService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), UserDTO.class));
		response.setCategories(SyncMapper.mapList(categoryService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), CategoryDTO.class));
		response.setMenuItems(SyncMapper.mapList(menuItemService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), MenuItemDTO.class));
		response.setItemVariants(SyncMapper.mapList(itemVariantService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), ItemVariantDTO.class));
		response.setStockLogs(SyncMapper.mapList(stockLogService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), StockLogDTO.class));
		response.setBills(SyncMapper.mapList(billService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), BillDTO.class));
		response.setBillItems(SyncMapper.mapList(billItemService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), BillItemDTO.class));
		response.setBillPayments(SyncMapper.mapList(billPaymentService.pullData(tenantId, lastSyncTimestamp, deviceId, shouldIgnoreDeviceId), BillPaymentDTO.class));

		int profilesCount = response.getProfiles() == null ? 0 : response.getProfiles().size();
		int usersCount = response.getUsers() == null ? 0 : response.getUsers().size();
		int categoriesCount = response.getCategories() == null ? 0 : response.getCategories().size();
		int menuItemsCount = response.getMenuItems() == null ? 0 : response.getMenuItems().size();
		int itemVariantsCount = response.getItemVariants() == null ? 0 : response.getItemVariants().size();
		int stockLogsCount = response.getStockLogs() == null ? 0 : response.getStockLogs().size();
		int billsCount = response.getBills() == null ? 0 : response.getBills().size();
		int billItemsCount = response.getBillItems() == null ? 0 : response.getBillItems().size();
		int billPaymentsCount = response.getBillPayments() == null ? 0 : response.getBillPayments().size();

		java.util.Map<String, Object> debugData = new java.util.HashMap<>();
		debugData.put("tenantIdPresent", tenantId != null);
		debugData.put("tenantId", tenantId == null ? -1L : tenantId);
		debugData.put("lastSyncTimestamp", lastSyncTimestamp == null ? -1L : lastSyncTimestamp);
		debugData.put("deviceId", deviceId);
		debugData.put("serverTimestamp", currentServerTime);
		debugData.put("profilesCount", profilesCount);
		debugData.put("usersCount", usersCount);
		debugData.put("categoriesCount", categoriesCount);
		debugData.put("menuItemsCount", menuItemsCount);
		debugData.put("itemVariantsCount", itemVariantsCount);
		debugData.put("stockLogsCount", stockLogsCount);
		debugData.put("billsCount", billsCount);
		debugData.put("billItemsCount", billItemsCount);
		debugData.put("billPaymentsCount", billPaymentsCount);

		DebugNDJSONLogger.log(
				"pre-debug",
				"H3_SYNC_RETURNS_EMPTY_OR_WRONG_TENANT",
				"MasterSyncController:pullMasterSync",
				"Master sync pull computed payload sizes",
				debugData
		);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/admin/test")
	public ResponseEntity<String> adminTest() {
		return ResponseEntity.ok("ADMIN_OK");
	}
}
