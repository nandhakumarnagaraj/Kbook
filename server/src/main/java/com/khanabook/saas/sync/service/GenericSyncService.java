package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.debug.DebugNDJSONLogger;
import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GenericSyncService {
	private static final Logger log = LoggerFactory.getLogger(GenericSyncService.class);

	@Autowired private BillRepository billRepository;
	@Autowired private MenuItemRepository menuItemRepository;
	@Autowired private ItemVariantRepository itemVariantRepository;
	@Autowired private CategoryRepository categoryRepository;

	@Transactional
	public <T extends BaseSyncEntity> PushSyncResponse handlePushSync(Long tenantId, List<T> payload,
			SyncRepository<T, Long> repository) {

		if (payload != null) {
			log.info("Starting handlePushSync for {} items of type {}", payload.size(), repository.getClass().getSimpleName());
		}

		if (payload != null && payload.size() > 500) {
			throw new IllegalArgumentException("Push payload exceeds maximum size of 500 items");
		}

		String role = com.khanabook.saas.security.TenantContext.getCurrentRole();
		boolean isKbookAdmin = "KBOOK_ADMIN".equals(role);

		if (tenantId == null && !isKbookAdmin) {
			throw new IllegalArgumentException(
					"Tenant ID (Restaurant ID) cannot be null. Ensure valid JWT is provided.");
		}

		if (payload == null || payload.isEmpty()) {
			return new PushSyncResponse(new ArrayList<>(), new ArrayList<>());
		}

		// Cross-tenant guard for OWNER: ensure every record's restaurantId matches tenantId
		if (!isKbookAdmin) {
			for (T record : payload) {
				if (record.getRestaurantId() != null && !record.getRestaurantId().equals(tenantId)) {
					throw new org.springframework.security.access.AccessDeniedException(
							"Permission denied: Accessing other restaurant data is forbidden.");
				}
			}
		}

		long distinctDevices = payload.stream()
				.map(r -> r.getDeviceId() != null ? r.getDeviceId() : "unknown")
				.distinct()
				.count();

		DebugNDJSONLogger.log(
				"pre-debug",
				"H4_PUSH_MERGE_ENGINE",
				"GenericSyncService:handlePushSync",
				"Push sync started",
				java.util.Map.of(
						"tenantId", tenantId != null ? tenantId : "null",
						"payloadSize", payload.size(),
						"distinctDevices", distinctDevices
				)
		);

		List<Long> successfulLocalIds = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();

		for (T record : payload) {
			if (record.getLocalId() == null && record.getId() != null) {
				record.setLocalId(record.getId());
				record.setId(null);
			}
		}

		Map<String, List<T>> recordsByDevice = payload.stream()
				.collect(Collectors.groupingBy(record -> record.getDeviceId() != null ? record.getDeviceId() : "unknown"));

		long serverTime = System.currentTimeMillis();
		List<T> allRecordsToSave = new ArrayList<>();

		for (Map.Entry<String, List<T>> entry : recordsByDevice.entrySet()) {
			String deviceId = entry.getKey();
			List<T> devicePayload = entry.getValue();

			List<Long> incomingLocalIds = devicePayload.stream().map(BaseSyncEntity::getLocalId)
					.filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());

			BaseSyncEntity firstRecord = devicePayload.get(0);
			boolean isSingletonType = firstRecord instanceof RestaurantProfile || firstRecord instanceof User;
			boolean singletonStylePayload = isSingletonType && devicePayload.size() == 1
					&& (incomingLocalIds.isEmpty() || incomingLocalIds.contains(1L));

			List<T> existingRecords = new ArrayList<>(
					repository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, incomingLocalIds));

			if (singletonStylePayload) {
				List<T> crossDeviceRecords = repository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L));

				for (T record : crossDeviceRecords) {
						boolean matchFound = false;
						if (record instanceof User existingUser && firstRecord instanceof User incomingUser) {
							if (existingUser.getLoginId() != null && incomingUser.getLoginId() != null
									&& existingUser.getLoginId().equalsIgnoreCase(incomingUser.getLoginId())) {
								matchFound = true;
							} else if (existingUser.getEmail() != null
									&& existingUser.getEmail().equalsIgnoreCase(incomingUser.getEmail())) {
								matchFound = true;
							}
						} else {
						matchFound = true;
					}

					if (matchFound) {
						boolean alreadyMatched = existingRecords.stream()
								.anyMatch(existing -> existing.getId() != null && existing.getId().equals(record.getId()));
						if (!alreadyMatched) {
							existingRecords.add(record);
						}
					}
				}
			}

			Map<Long, T> existingRecordMap = existingRecords.stream()
					.collect(Collectors.toMap(BaseSyncEntity::getLocalId, Function.identity(), (existing,
							replacement) -> existing.getUpdatedAt() > replacement.getUpdatedAt() ? existing : replacement));

			Map<Long, T> recordsToSaveMap = new HashMap<>();

			for (T incomingRecord : devicePayload) {
				try {
					log.info("Processing push record: localId={}, type={}", incomingRecord.getLocalId(), incomingRecord.getClass().getSimpleName());
					if (incomingRecord.getLocalId() == null) {
						if (singletonStylePayload) {
							incomingRecord.setLocalId(1L);
						} else {
							log.warn("Skipping record with NULL localId for device: {}", deviceId);
							continue;
						}
					}

					incomingRecord.setRestaurantId(tenantId);
					incomingRecord.setServerUpdatedAt(serverTime);

					if (incomingRecord.getCreatedAt() == null) {
						incomingRecord.setCreatedAt(
								incomingRecord.getUpdatedAt() != null ? incomingRecord.getUpdatedAt() : serverTime);
					}

					if (incomingRecord instanceof Bill bill) {
						if (bill.getLastResetDate() == null || bill.getLastResetDate().isEmpty()) {
							bill.setLastResetDate(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
						}
					}

					T existingRecord = null;
					if (incomingRecord.getLocalId() != null) {
						if (incomingRecord.getId() != null) {
							existingRecord = existingRecords.stream().filter(r -> incomingRecord.getId().equals(r.getId()))
									.findFirst().orElse(null);
						}

						if (existingRecord == null) {
							existingRecord = existingRecordMap.get(incomingRecord.getLocalId());
						}
					}

					// For KBOOK_ADMIN, ensure we use the record's restaurantId if tenantId is null
					Long targetTenantId = tenantId != null ? tenantId : incomingRecord.getRestaurantId();
					if (targetTenantId == null) {
						log.warn("Skipping record with NULL restaurantId for device: {}", deviceId);
						continue;
					}

					incomingRecord.setRestaurantId(targetTenantId);
					incomingRecord.setServerUpdatedAt(serverTime);
					if (incomingRecord.getIsDeleted() == null) {
						incomingRecord.setIsDeleted(false);
					}

					if (incomingRecord.getCreatedAt() == null) {
						incomingRecord.setCreatedAt(
								incomingRecord.getUpdatedAt() != null ? incomingRecord.getUpdatedAt() : serverTime);
					}

					if (incomingRecord instanceof Bill bill) {
						if (bill.getLastResetDate() == null || bill.getLastResetDate().isEmpty()) {
							bill.setLastResetDate(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
						}
					}

					if (existingRecord != null) {
						if (incomingRecord.getUpdatedAt() > existingRecord.getUpdatedAt()) {

								if (incomingRecord instanceof User user && existingRecord instanceof User existingUser) {
									if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
										user.setPasswordHash(existingUser.getPasswordHash());
									}
									if (user.getLoginId() == null || user.getLoginId().trim().isEmpty()) {
										user.setLoginId(existingUser.getLoginId());
									}
									if (user.getAuthProvider() == null) {
										user.setAuthProvider(existingUser.getAuthProvider() != null ? existingUser.getAuthProvider()
												: AuthProvider.PHONE);
									}
									// Data Overwrite Protection: Don't overwrite email with null/empty from app
									if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
										user.setEmail(existingUser.getEmail());
									} else if ((existingUser.getAuthProvider() == AuthProvider.GOOGLE)
											&& existingUser.getEmail() != null
											&& !existingUser.getEmail().equalsIgnoreCase(user.getEmail())) {
										// Preserve Google-linked identity when a synced mobile/profile payload carries a phone number.
										user.setEmail(existingUser.getEmail());
									}
								
									if (user.getGoogleEmail() == null || user.getGoogleEmail().trim().isEmpty()) {
										user.setGoogleEmail(existingUser.getGoogleEmail());
									}

									if (user.getIsActive() == null) {
										user.setIsActive(existingUser.getIsActive() != null ? existingUser.getIsActive() : true);
									}

									// Prevent duplicate email/phone numbers from crashing the batch sync
										if (repository instanceof com.khanabook.saas.repository.UserRepository) {
										com.khanabook.saas.repository.UserRepository userRepo = (com.khanabook.saas.repository.UserRepository) repository;

										if (existingUser.getLoginId() != null && user.getLoginId() != null
												&& !existingUser.getLoginId().equalsIgnoreCase(user.getLoginId())) {
											if (userRepo.existsByLoginId(user.getLoginId())) {
												throw new RuntimeException("Sync rejected: Login identity already exists for another user");
											}
										}
										
										if (existingUser.getEmail() != null && user.getEmail() != null && !existingUser.getEmail().equalsIgnoreCase(user.getEmail())) {
											if (userRepo.existsByEmail(user.getEmail())) {
											throw new RuntimeException("Sync rejected: Email/Phone already exists for another user");
										}
									}
									
									if (existingUser.getWhatsappNumber() != null && user.getWhatsappNumber() != null && !existingUser.getWhatsappNumber().equalsIgnoreCase(user.getWhatsappNumber())) {
										if (userRepo.existsByWhatsappNumber(user.getWhatsappNumber())) {
											throw new RuntimeException("Sync rejected: Whatsapp number already exists for another user");
										}
									}
								}
							}
							
							// Relational ID Resolution for Updates
							resolveRelationalIds(incomingRecord, targetTenantId, deviceId);

							incomingRecord.setId(existingRecord.getId());

							T staged = recordsToSaveMap.get(incomingRecord.getLocalId());
							if (staged == null || incomingRecord.getUpdatedAt() > staged.getUpdatedAt()) {
								recordsToSaveMap.put(incomingRecord.getLocalId(), incomingRecord);
							}
							successfulLocalIds.add(incomingRecord.getLocalId());
						} else {
							successfulLocalIds.add(incomingRecord.getLocalId());
						}
					} else {
						// Relational ID Resolution for New Records
						resolveRelationalIds(incomingRecord, targetTenantId, deviceId);

						T staged = recordsToSaveMap.get(incomingRecord.getLocalId());
						if (staged == null || incomingRecord.getUpdatedAt() > staged.getUpdatedAt()) {
							recordsToSaveMap.put(incomingRecord.getLocalId(), incomingRecord);
						}
						successfulLocalIds.add(incomingRecord.getLocalId());
					}
				} catch (Exception e) {
					log.error("Sync Error for record class {}: {}", incomingRecord.getClass().getSimpleName(), e.getMessage());
					e.printStackTrace();
					DebugNDJSONLogger.log(
							"pre-debug",
							"H4_PUSH_MERGE_ENGINE",
							"GenericSyncService:handlePushSync",
							"Sync Error while staging a record",
							java.util.Map.of(
									"deviceIdPresent", incomingRecord.getDeviceId() != null,
									"deviceId", incomingRecord.getDeviceId() == null ? "null" : incomingRecord.getDeviceId(),
									"exceptionClass", e.getClass().getSimpleName(),
									"exceptionMessage", e.getMessage() == null ? "" : e.getMessage()
							)
					);
				}
			}
			
			allRecordsToSave.addAll(recordsToSaveMap.values());
		}

		if (!allRecordsToSave.isEmpty()) {
			repository.saveAll(allRecordsToSave);
		}

		log.info("Successfully batch synced {} records for Tenant ID: {}", successfulLocalIds.size(), tenantId);

		DebugNDJSONLogger.log(
				"pre-debug",
				"H4_PUSH_MERGE_ENGINE",
				"GenericSyncService:handlePushSync",
				"Push sync completed",
				java.util.Map.of(
						"tenantId", tenantId != null ? tenantId : "null",
						"successfulLocalIdsSize", successfulLocalIds.size(),
						"failedLocalIdsSize", failedLocalIds.size(),
						"recordsSavedSize", allRecordsToSave.size()
				)
		);

		return new PushSyncResponse(successfulLocalIds, failedLocalIds);
	}

	private void resolveRelationalIds(BaseSyncEntity record, Long tenantId, String deviceId) {
		try {
			if (record instanceof MenuItem menuItem) {
				if (menuItem.getCategoryId() != null && menuItem.getServerCategoryId() == null) {
					log.info("Resolving Category for MenuItem: localId={}, categoryId={}", menuItem.getLocalId(), menuItem.getCategoryId());
					categoryRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, menuItem.getCategoryId())
							.or(() -> categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(menuItem.getCategoryId())).stream().findFirst())
							.ifPresent(cat -> {
								menuItem.setServerCategoryId(cat.getId());
								log.info("Resolved Category: serverId={}", cat.getId());
							});
				}
			} else if (record instanceof ItemVariant variant) {
				if (variant.getMenuItemId() != null && variant.getServerMenuItemId() == null) {
					log.info("Resolving MenuItem for ItemVariant: localId={}, menuItemId={}", variant.getLocalId(), variant.getMenuItemId());
					menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, variant.getMenuItemId())
							.or(() -> menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(variant.getMenuItemId())).stream().findFirst())
							.ifPresent(item -> {
								variant.setServerMenuItemId(item.getId());
								log.info("Resolved MenuItem: serverId={}", item.getId());
							});
				}
			} else if (record instanceof BillItem billItem) {
				log.info("Resolving for BillItem: localId={}, billId={}, menuItemId={}, variantId={}", 
						billItem.getLocalId(), billItem.getBillId(), billItem.getMenuItemId(), billItem.getVariantId());
				
				if (billItem.getBillId() != null && billItem.getServerBillId() == null) {
					billRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, billItem.getBillId())
							.or(() -> billRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(billItem.getBillId())).stream().findFirst())
							.ifPresent(bill -> {
								billItem.setServerBillId(bill.getId());
								log.info("Resolved Bill: serverId={}", bill.getId());
							});
				}
				if (billItem.getMenuItemId() != null && billItem.getServerMenuItemId() == null) {
					menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, billItem.getMenuItemId())
							.or(() -> menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(billItem.getMenuItemId())).stream().findFirst())
							.ifPresent(item -> {
								billItem.setServerMenuItemId(item.getId());
								log.info("Resolved MenuItem: serverId={}", item.getId());
							});
				}
				if (billItem.getVariantId() != null && billItem.getServerVariantId() == null) {
					itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, billItem.getVariantId())
							.or(() -> itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(billItem.getVariantId())).stream().findFirst())
							.ifPresent(v -> {
								billItem.setServerVariantId(v.getId());
								log.info("Resolved Variant: serverId={}", v.getId());
							});
				}
			} else if (record instanceof BillPayment payment) {
				if (payment.getBillId() != null && payment.getServerBillId() == null) {
					log.info("Resolving Bill for BillPayment: localId={}, billId={}", payment.getLocalId(), payment.getBillId());
					billRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, payment.getBillId())
							.or(() -> billRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(payment.getBillId())).stream().findFirst())
							.ifPresent(bill -> {
								payment.setServerBillId(bill.getId());
								log.info("Resolved Bill: serverId={}", bill.getId());
							});
				}
			} else if (record instanceof StockLog logRecord) {
				if (logRecord.getMenuItemId() != null && logRecord.getServerMenuItemId() == null) {
					log.info("Resolving MenuItem for StockLog: localId={}, menuItemId={}", logRecord.getLocalId(), logRecord.getMenuItemId());
					menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, logRecord.getMenuItemId())
							.or(() -> menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(logRecord.getMenuItemId())).stream().findFirst())
							.ifPresent(item -> {
								logRecord.setServerMenuItemId(item.getId());
								log.info("Resolved MenuItem: serverId={}", item.getId());
							});
				}
				if (logRecord.getVariantId() != null && logRecord.getServerVariantId() == null) {
					log.info("Resolving Variant for StockLog: localId={}, variantId={}", logRecord.getLocalId(), logRecord.getVariantId());
					itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalId(tenantId, deviceId, logRecord.getVariantId())
							.or(() -> itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(logRecord.getVariantId())).stream().findFirst())
							.ifPresent(v -> {
								logRecord.setServerVariantId(v.getId());
								log.info("Resolved Variant: serverId={}", v.getId());
							});
				}
			}
		} catch (Exception e) {
			log.error("Resolution Failed: {}", e.getMessage());
			e.printStackTrace();
		}
	}
}
