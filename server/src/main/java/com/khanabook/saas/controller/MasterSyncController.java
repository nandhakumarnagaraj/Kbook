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
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.service.*;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
	private final FeatureFlagService featureFlagService;
	private final com.khanabook.saas.service.PermissionService permissionService;

	// Phase C strict mode and the legacy compatibility fallback (Correction 2).
	// strict=true: a missing terminal token rejects terminal-operational pulls.
	// compatibility=false: the legacy client-supplied terminal id is no longer trusted;
	// a missing token is rejected even outside strict mode.
	@Value("${terminal.sync.strict:false}")
	private boolean terminalSyncStrict;

	@Value("${terminal.sync.compatibility:true}")
	private boolean terminalCompatibility;
	private final EasebuzzSubMerchantRepository subMerchantRepo;
	private final RestaurantProfileRepository profileRepo;
	private final SubMerchantService subMerchantService;

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

		// Idempotent auto-enable: ensures Easebuzz/Zomato/Swiggy flags are turned on
		// whenever their credentials/sub-merchants are configured but the flag is still off.
		autoEnableEasebuzzForExistingSubMerchants(tenantId);
		autoEnableMarketplaceIfConfigured(tenantId);

		boolean sharedDataCrossDevice = ignoreDeviceId || firstSync;
		boolean transactionalCrossDevice = ignoreDeviceId;

		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
				page, size, org.springframework.data.domain.Sort.by("id").ascending());

		// Terminal identity is always taken from the authenticated X-Terminal-Token, never
		// from the client query parameter. A supplied query terminal id is only honoured as a
		// legacy fallback while compatibility mode is enabled; any mismatch with the token is
		// rejected so Terminal B cannot request Terminal A's operational records.
		String authenticatedTerminalId = TenantContext.getCurrentTerminalId();
		boolean isAdmin = "KBOOK_ADMIN".equals(role);
		String effectiveTerminalId;
		if (authenticatedTerminalId != null && !authenticatedTerminalId.isBlank()) {
			effectiveTerminalId = authenticatedTerminalId;
			if (terminalId != null && !terminalId.isBlank() && !terminalId.equals(authenticatedTerminalId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
						"Requested terminal does not match authenticated terminal");
			}
		} else if (terminalId != null && !terminalId.isBlank()) {
			if (!terminalCompatibility) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
						"Terminal identity required; compatibility mode is disabled");
			}
			effectiveTerminalId = terminalId; // legacy fallback only while compatibility mode is enabled
		} else {
			effectiveTerminalId = null;
		}

		// Terminal-operational pulls (bills/items/payments) require a terminal identity once
		// strict mode is enabled or compatibility mode is turned off. Admins remain exempt.
		boolean terminalIdentityRequired = !isAdmin
				&& (terminalSyncStrict || !terminalCompatibility)
				&& (effectiveTerminalId == null || effectiveTerminalId.isBlank());
		if (terminalIdentityRequired) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Terminal identity required for bill pull: activate a terminal and send X-Terminal-Token");
		}

		MasterSyncResponseDTO response = new MasterSyncResponseDTO();
		response.setServerTimestamp(currentServerTime);

		// Effective feature-flag state for this restaurant (Req 30.23). Additive:
		// sent on page 0 once, omitted from follow-up pages.
		if (page == 0) {
			response.setEnabledFeatures(featureFlagService.resolveAllForRestaurant(tenantId));
			Long currentUserId = com.khanabook.saas.security.TenantContext.getCurrentUserId();
			if (currentUserId != null) {
				response.setGrantedPermissions(permissionService.getGrantedPermissions(tenantId, currentUserId));
			}
		}

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
				billService.pullData(tenantId, lastSyncTimestamp, deviceId, effectiveTerminalId, transactionalCrossDevice, pageable);
		java.util.List<Long> pulledBillIds = billsPage.getContent().stream()
				.map(com.khanabook.saas.entity.Bill::getId)
				.filter(java.util.Objects::nonNull)
				.toList();
		java.util.List<com.khanabook.saas.entity.BillItem> terminalUpdatedBillItems =
				effectiveTerminalId == null || effectiveTerminalId.isBlank()
						? java.util.Collections.emptyList()
						: billItemRepository.findUpdatedForTerminal(tenantId, lastSyncTimestamp, effectiveTerminalId);
		java.util.List<com.khanabook.saas.entity.BillPayment> terminalUpdatedBillPayments =
				effectiveTerminalId == null || effectiveTerminalId.isBlank()
						? java.util.Collections.emptyList()
						: billPaymentRepository.findUpdatedForTerminal(tenantId, lastSyncTimestamp, effectiveTerminalId);
		java.util.List<com.khanabook.saas.entity.BillItem> pulledBillItems = pulledBillIds.isEmpty()
				? java.util.Collections.emptyList()
				: billItemRepository.findByRestaurantIdAndServerBillIdIn(tenantId, pulledBillIds);
		java.util.List<com.khanabook.saas.entity.BillPayment> pulledBillPayments = pulledBillIds.isEmpty()
				? java.util.Collections.emptyList()
				: billPaymentRepository.findByRestaurantIdAndServerBillIdIn(tenantId, pulledBillIds);
		java.util.List<com.khanabook.saas.entity.BillItem> billItems = mergeById(
				terminalUpdatedBillItems, pulledBillItems);
		java.util.List<com.khanabook.saas.entity.BillPayment> billPayments = mergeById(
				terminalUpdatedBillPayments, pulledBillPayments);

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

	private static <T> java.util.List<T> mergeById(java.util.List<T> first, java.util.List<T> second) {
		java.util.LinkedHashMap<Object, T> merged = new java.util.LinkedHashMap<>();
		for (T item : first) {
			merged.put(entityId(item), item);
		}
		for (T item : second) {
			merged.putIfAbsent(entityId(item), item);
		}
		return new java.util.ArrayList<>(merged.values());
	}

	private static Object entityId(Object entity) {
		if (entity instanceof com.khanabook.saas.entity.BillItem item && item.getId() != null) {
			return item.getId();
		}
		if (entity instanceof com.khanabook.saas.entity.BillPayment payment && payment.getId() != null) {
			return payment.getId();
		}
		return System.identityHashCode(entity);
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
