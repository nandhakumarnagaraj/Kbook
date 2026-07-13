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
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
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
	private final BillItemRepository billItemRepository;
	private final BillPaymentRepository billPaymentRepository;

	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	@GetMapping("/pull")
	public ResponseEntity<MasterSyncResponseDTO> pullMasterSync(@RequestParam Long lastSyncTimestamp,
			@RequestParam String deviceId, @RequestParam(required = false) Long restaurantId,
			@RequestParam(required = false) String terminalId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "500") int size,
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
		boolean transactionalCrossDevice = ignoreDeviceId;

		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
				page, size, org.springframework.data.domain.Sort.by("id").ascending());

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);

		if (page == 0) {
			response.setProfiles(SyncMapper.mapList(restaurantProfileService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), RestaurantProfileDTO.class));
			response.setUsers(SyncMapper.mapList(userService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), UserDTO.class));
			response.setCategories(SyncMapper.mapList(categoryService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), CategoryDTO.class));
			response.setMenuItems(SyncMapper.mapList(menuItemService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), MenuItemDTO.class));
			response.setItemVariants(SyncMapper.mapList(itemVariantService.pullData(tenantId, lastSyncTimestamp, deviceId, sharedDataCrossDevice), ItemVariantDTO.class));
		} else {
			response.setProfiles(java.util.Collections.emptyList());
			response.setUsers(java.util.Collections.emptyList());
			response.setCategories(java.util.Collections.emptyList());
			response.setMenuItems(java.util.Collections.emptyList());
			response.setItemVariants(java.util.Collections.emptyList());
		}

		org.springframework.data.domain.Page<com.khanabook.saas.entity.StockLog> stockLogsPage =
				stockLogService.pullData(tenantId, lastSyncTimestamp, deviceId, transactionalCrossDevice, pageable);
		org.springframework.data.domain.Page<com.khanabook.saas.entity.Bill> billsPage =
				billService.pullData(tenantId, lastSyncTimestamp, deviceId, terminalId, transactionalCrossDevice, pageable);
		java.util.List<com.khanabook.saas.entity.BillItem> billItems =
				terminalId == null || terminalId.isBlank()
						? java.util.Collections.emptyList()
						: billItemRepository.findUpdatedForTerminal(tenantId, lastSyncTimestamp, terminalId);
		java.util.List<com.khanabook.saas.entity.BillPayment> billPayments =
				terminalId == null || terminalId.isBlank()
						? java.util.Collections.emptyList()
						: billPaymentRepository.findUpdatedForTerminal(tenantId, lastSyncTimestamp, terminalId);

		response.setStockLogs(SyncMapper.mapList(stockLogsPage.getContent(), StockLogDTO.class));
		response.setBills(SyncMapper.mapList(billsPage.getContent(), BillDTO.class));
		response.setBillItems(SyncMapper.mapList(billItems, BillItemDTO.class));
		response.setBillPayments(SyncMapper.mapList(billPayments, BillPaymentDTO.class));

		boolean hasMore = stockLogsPage.hasNext() || billsPage.hasNext();
		response.setHasMore(hasMore);
		if (hasMore) {
			response.setNextPage(page + 1);
		}

		int profilesCount = response.getProfiles() == null ? 0 : response.getProfiles().size();
		int usersCount = response.getUsers() == null ? 0 : response.getUsers().size();
		int categoriesCount = response.getCategories() == null ? 0 : response.getCategories().size();
		int menuItemsCount = response.getMenuItems() == null ? 0 : response.getMenuItems().size();
		int itemVariantsCount = response.getItemVariants() == null ? 0 : response.getItemVariants().size();
		int stockLogsCount = response.getStockLogs() == null ? 0 : response.getStockLogs().size();
		int billsCount = response.getBills() == null ? 0 : response.getBills().size();
		int billItemsCount = response.getBillItems() == null ? 0 : response.getBillItems().size();
		int billPaymentsCount = response.getBillPayments() == null ? 0 : response.getBillPayments().size();

		log.info("Master sync pull tenantId={} deviceId={} page={} size={} hasMore={} firstSync={} explicitIgnoreDeviceId={} sharedDataCrossDevice={} transactionalCrossDevice={} profiles={} users={} categories={} " +
				"menuItems={} variants={} stockLogs={} bills={} billItems={} billPayments={}",
				tenantId, deviceId, page, size, hasMore, firstSync, ignoreDeviceId, sharedDataCrossDevice, transactionalCrossDevice,
				profilesCount, usersCount, categoriesCount,
				menuItemsCount, itemVariantsCount, stockLogsCount, billsCount, billItemsCount, billPaymentsCount);

		return ResponseEntity.ok(response);
	}
}
