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

import java.util.*;
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

			// Build bulk ID maps once per device batch — eliminates N+1 queries
			RelationalIdMaps idMaps = buildRelationalIdMaps(devicePayload, tenantId, deviceId);

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
							resolveRelationalIds(incomingRecord, idMaps);

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
						resolveRelationalIds(incomingRecord, idMaps);

						T staged = recordsToSaveMap.get(incomingRecord.getLocalId());
						if (staged == null || incomingRecord.getUpdatedAt() > staged.getUpdatedAt()) {
							recordsToSaveMap.put(incomingRecord.getLocalId(), incomingRecord);
						}
						successfulLocalIds.add(incomingRecord.getLocalId());
					}
				} catch (Exception e) {
					log.error("Sync Error for record class {}: {}", incomingRecord.getClass().getSimpleName(), e.getMessage(), e);
					if (incomingRecord.getLocalId() != null) {
						failedLocalIds.add(incomingRecord.getLocalId());
					}
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

	/**
	 * Builds lookup maps for all FK localIds present in a device batch.
	 * ONE bulk query per entity type instead of one query per record —
	 * reduces 1500 queries (500 BillItems × 3 FKs) to ~8 queries total.
	 */
	private RelationalIdMaps buildRelationalIdMaps(List<? extends BaseSyncEntity> devicePayload,
			Long tenantId, String deviceId) {

		// Collect all referenced localIds from the payload by type
		Set<Long> billLocalIds      = new HashSet<>();
		Set<Long> menuItemLocalIds  = new HashSet<>();
		Set<Long> variantLocalIds   = new HashSet<>();
		Set<Long> categoryLocalIds  = new HashSet<>();

		for (BaseSyncEntity record : devicePayload) {
			if (record instanceof BillItem bi) {
				if (bi.getBillId()     != null) billLocalIds.add(bi.getBillId());
				if (bi.getMenuItemId() != null) menuItemLocalIds.add(bi.getMenuItemId());
				if (bi.getVariantId()  != null) variantLocalIds.add(bi.getVariantId());
			} else if (record instanceof BillPayment bp) {
				if (bp.getBillId()     != null) billLocalIds.add(bp.getBillId());
			} else if (record instanceof ItemVariant iv) {
				if (iv.getMenuItemId() != null) menuItemLocalIds.add(iv.getMenuItemId());
			} else if (record instanceof MenuItem mi) {
				if (mi.getCategoryId() != null) categoryLocalIds.add(mi.getCategoryId());
			} else if (record instanceof StockLog sl) {
				if (sl.getMenuItemId() != null) menuItemLocalIds.add(sl.getMenuItemId());
				if (sl.getVariantId()  != null) variantLocalIds.add(sl.getVariantId());
			}
		}

		// Bulk fetch — device-specific lookup first, then cross-device fallback,
		// merged so device-specific wins on collision.
		RelationalIdMaps maps = new RelationalIdMaps();

		if (!billLocalIds.isEmpty()) {
			maps.billLocalToServerId = buildMergedMap(
					billRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(billLocalIds)),
					billRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(billLocalIds)));
		}
		if (!menuItemLocalIds.isEmpty()) {
			maps.menuItemLocalToServerId = buildMergedMap(
					menuItemRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(menuItemLocalIds)),
					menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(menuItemLocalIds)));
		}
		if (!variantLocalIds.isEmpty()) {
			maps.variantLocalToServerId = buildMergedMap(
					itemVariantRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(variantLocalIds)),
					itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(variantLocalIds)));
		}
		if (!categoryLocalIds.isEmpty()) {
			maps.categoryLocalToServerId = buildMergedMap(
					categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, new ArrayList<>(categoryLocalIds)),
					categoryRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(tenantId, deviceId, new ArrayList<>(categoryLocalIds)));
		}

		return maps;
	}

	/** Cross-device results first (lower priority), device-specific second (higher priority). */
	private Map<Long, Long> buildMergedMap(List<? extends BaseSyncEntity> crossDevice,
			List<? extends BaseSyncEntity> deviceSpecific) {
		Map<Long, Long> map = new HashMap<>();
		for (BaseSyncEntity e : crossDevice) {
			if (e.getLocalId() != null && e.getId() != null) map.put(e.getLocalId(), e.getId());
		}
		for (BaseSyncEntity e : deviceSpecific) {
			if (e.getLocalId() != null && e.getId() != null) map.put(e.getLocalId(), e.getId());
		}
		return map;
	}

	private static class RelationalIdMaps {
		Map<Long, Long> billLocalToServerId      = Collections.emptyMap();
		Map<Long, Long> menuItemLocalToServerId  = Collections.emptyMap();
		Map<Long, Long> variantLocalToServerId   = Collections.emptyMap();
		Map<Long, Long> categoryLocalToServerId  = Collections.emptyMap();
	}

	private void resolveRelationalIds(BaseSyncEntity record, RelationalIdMaps maps) {
		try {
			if (record instanceof MenuItem menuItem) {
				if (menuItem.getCategoryId() != null && menuItem.getServerCategoryId() == null) {
					Long serverId = maps.categoryLocalToServerId.get(menuItem.getCategoryId());
					if (serverId != null) menuItem.setServerCategoryId(serverId);
				}
			} else if (record instanceof ItemVariant variant) {
				if (variant.getMenuItemId() != null && variant.getServerMenuItemId() == null) {
					Long serverId = maps.menuItemLocalToServerId.get(variant.getMenuItemId());
					if (serverId != null) variant.setServerMenuItemId(serverId);
				}
			} else if (record instanceof BillItem billItem) {
				if (billItem.getBillId() != null && billItem.getServerBillId() == null) {
					Long serverId = maps.billLocalToServerId.get(billItem.getBillId());
					if (serverId != null) billItem.setServerBillId(serverId);
				}
				if (billItem.getMenuItemId() != null && billItem.getServerMenuItemId() == null) {
					Long serverId = maps.menuItemLocalToServerId.get(billItem.getMenuItemId());
					if (serverId != null) billItem.setServerMenuItemId(serverId);
				}
				if (billItem.getVariantId() != null && billItem.getServerVariantId() == null) {
					Long serverId = maps.variantLocalToServerId.get(billItem.getVariantId());
					if (serverId != null) billItem.setServerVariantId(serverId);
				}
			} else if (record instanceof BillPayment payment) {
				if (payment.getBillId() != null && payment.getServerBillId() == null) {
					Long serverId = maps.billLocalToServerId.get(payment.getBillId());
					if (serverId != null) payment.setServerBillId(serverId);
				}
			} else if (record instanceof StockLog logRecord) {
				if (logRecord.getMenuItemId() != null && logRecord.getServerMenuItemId() == null) {
					Long serverId = maps.menuItemLocalToServerId.get(logRecord.getMenuItemId());
					if (serverId != null) logRecord.setServerMenuItemId(serverId);
				}
				if (logRecord.getVariantId() != null && logRecord.getServerVariantId() == null) {
					Long serverId = maps.variantLocalToServerId.get(logRecord.getVariantId());
					if (serverId != null) logRecord.setServerVariantId(serverId);
				}
			}
		} catch (Exception e) {
			log.error("Resolution Failed: {}", e.getMessage(), e);
		}
	}
}
