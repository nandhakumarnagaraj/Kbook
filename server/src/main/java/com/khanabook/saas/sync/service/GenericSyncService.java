package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.sync.repository.SyncRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenericSyncService {
	private static final Logger log = LoggerFactory.getLogger(GenericSyncService.class);
	private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

	private final BillRepository billRepository;
	private final MenuItemRepository menuItemRepository;
	private final ItemVariantRepository itemVariantRepository;
	private final CategoryRepository categoryRepository;
	private final BillPaymentRepository billPaymentRepository;
	private final RestaurantTerminalRepository terminalRepository;

	private User findExistingUserByIdentity(Long tenantId, User incomingUser,
			com.khanabook.saas.repository.UserRepository userRepository) {
		if (incomingUser.getId() != null) {
			Optional<User> byServerId = userRepository.findById(incomingUser.getId());
			if (byServerId.isPresent() && byServerId.get().getRestaurantId().equals(tenantId)) {
				return byServerId.get();
			}
		}

		if (incomingUser.getLoginId() != null && !incomingUser.getLoginId().isBlank()) {
			Optional<User> byLoginId = userRepository.findByLoginIdIgnoreCase(incomingUser.getLoginId());
			if (byLoginId.isPresent() && byLoginId.get().getRestaurantId().equals(tenantId)) {
				return byLoginId.get();
			}
		}

		if (incomingUser.getEmail() != null && !incomingUser.getEmail().isBlank()) {
			Optional<User> byEmail = userRepository.findByEmailIgnoreCase(incomingUser.getEmail());
			if (byEmail.isPresent() && byEmail.get().getRestaurantId().equals(tenantId)) {
				return byEmail.get();
			}
		}

		if (incomingUser.getGoogleEmail() != null && !incomingUser.getGoogleEmail().isBlank()) {
			Optional<User> byGoogleEmail = userRepository.findByGoogleEmailIgnoreCase(incomingUser.getGoogleEmail());
			if (byGoogleEmail.isPresent() && byGoogleEmail.get().getRestaurantId().equals(tenantId)) {
				return byGoogleEmail.get();
			}
		}

		if (incomingUser.getWhatsappNumber() != null && !incomingUser.getWhatsappNumber().isBlank()) {
			Optional<User> byWhatsapp = userRepository.findByWhatsappNumber(incomingUser.getWhatsappNumber());
			if (byWhatsapp.isPresent() && byWhatsapp.get().getRestaurantId().equals(tenantId)) {
				return byWhatsapp.get();
			}
		}

		return null;
	}

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

		// Cross-tenant guard for OWNER: warn and auto-fix records that carry a *different*
		// restaurant's ID. The JWT already enforces tenant scope, so we trust tenantId
		// and silently correct any mismatches from old client data.
		if (!isKbookAdmin) {
			for (T record : payload) {
				Long rid = record.getRestaurantId();
				if (rid == null || rid == 0L) {
					log.warn("restaurantId unset on {} record (device={}) — will be assigned tenantId={}",
							record.getClass().getSimpleName(), record.getDeviceId(), tenantId);
				} else if (!rid.equals(tenantId)) {
					log.warn("restaurantId mismatch on {} record (device={}): got {} expected {} — auto-correcting",
							record.getClass().getSimpleName(), record.getDeviceId(), rid, tenantId);
				}
			}
		}

		long distinctDevices = payload.stream()
				.map(r -> r.getDeviceId() != null ? r.getDeviceId() : "unknown")
				.distinct()
				.count();

		log.info("Push sync started tenantId={} payloadSize={} distinctDevices={}",
				tenantId, payload.size(), distinctDevices);

		List<Long> successfulLocalIds = new ArrayList<>();
		List<Long> failedLocalIds = new ArrayList<>();
		Map<Long, String> failedReasons = new HashMap<>();
		Map<Long, Long> localToServerIdMap = new HashMap<>();

		for (T record : payload) {
			if (record.getLocalId() == null && record.getId() != null) {
				record.setLocalId(record.getId());
				record.setId(null);
			}
			// Always enforce the JWT tenant — never trust the client-sent restaurantId
			if (!isKbookAdmin) {
				record.setRestaurantId(tenantId);
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
					.filter(Objects::nonNull).distinct().collect(Collectors.toList());

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
							bill.setLastResetDate(java.time.LocalDate.now().toString());
						}
						if (bill.getRefundAmount() == null) {
							bill.setRefundAmount(java.math.BigDecimal.ZERO);
						}
					}

					// For KBOOK_ADMIN, ensure we use the record's restaurantId if tenantId is null
					Long targetTenantId = tenantId != null ? tenantId : incomingRecord.getRestaurantId();
					if (targetTenantId == null) {
						log.warn("Skipping record with NULL restaurantId for device: {}", deviceId);
						continue;
					}

					T existingRecord = null;
					if (incomingRecord.getLocalId() != null) {
						if (incomingRecord.getId() != null) {
							existingRecord = existingRecords.stream().filter(r -> incomingRecord.getId().equals(r.getId()))
									.findFirst().orElse(null);
						}

						// ── Priority 2: publicToken (canonical identity) — checked BEFORE
						// (deviceId, localId) to prevent wrong-row match on localId reuse ──
						if (existingRecord == null
								&& incomingRecord instanceof Bill incomingBill
								&& repository instanceof com.khanabook.saas.repository.BillRepository billRepo
								&& incomingBill.getPublicToken() != null) {
							existingRecord = (T) billRepo
									.findByRestaurantIdAndPublicTokenAndIsDeletedFalse(targetTenantId, incomingBill.getPublicToken())
									.orElse(null);
						}

						// ── Priority 3: (deviceId, localId) — legacy fallback ──
						if (existingRecord == null) {
							existingRecord = existingRecordMap.get(incomingRecord.getLocalId());
						}
					}

					if (existingRecord == null && incomingRecord.getId() != null) {
						existingRecord = repository.findById(incomingRecord.getId())
								.filter(record -> Objects.equals(record.getRestaurantId(), targetTenantId))
								.orElse(null);
					}

					if (existingRecord == null
							&& incomingRecord instanceof User incomingUser
							&& repository instanceof com.khanabook.saas.repository.UserRepository userRepository) {
						existingRecord = (T) findExistingUserByIdentity(targetTenantId, incomingUser, userRepository);
					}


					incomingRecord.setServerUpdatedAt(serverTime);
					if (incomingRecord.getIsDeleted() == null) {
						incomingRecord.setIsDeleted(false);
					}

					if (existingRecord != null) {
						if (incomingRecord.getUpdatedAt() >= existingRecord.getUpdatedAt()) {

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

							if (incomingRecord instanceof RestaurantProfile incomingProfile
									&& existingRecord instanceof RestaurantProfile existingProfile) {
								incomingProfile.setTimezone(DEFAULT_TIMEZONE);
								mergeCounterState(incomingProfile, existingProfile);
							}

							// Refund preservation: refundAmount is server-owned (set by admin via
							// markManualRefund). Android never sends it, so ALWAYS restore the
							// server value on updates to prevent a push from zeroing a refund.
							if (incomingRecord instanceof Bill incomingBill
									&& existingRecord instanceof Bill existingBill) {
								incomingBill.setRefundAmount(existingBill.getRefundAmount());
							}

							incomingRecord.setId(existingRecord.getId());
							// Preserve the current row version so sync updates don't trip optimistic locking
							// when the client payload carries a stale/default version value.
							incomingRecord.setVersion(existingRecord.getVersion());

							T staged = recordsToSaveMap.get(incomingRecord.getLocalId());
							if (staged == null || incomingRecord.getUpdatedAt() > staged.getUpdatedAt()) {
								recordsToSaveMap.put(incomingRecord.getLocalId(), incomingRecord);
							}
							successfulLocalIds.add(incomingRecord.getLocalId());
						} else if (isTransactionalIdempotentRetry(incomingRecord, existingRecord)) {
							Long localId = incomingRecord.getLocalId();
							successfulLocalIds.add(localId);
							localToServerIdMap.put(localId, existingRecord.getId());
							log.info("Acknowledged idempotent retry type={} tenantId={} deviceId={} localId={} serverId={}",
									incomingRecord.getClass().getSimpleName(), targetTenantId, incomingRecord.getDeviceId(),
									localId, existingRecord.getId());
						} else {
							Long failedLocalId = incomingRecord.getLocalId();
							failedLocalIds.add(failedLocalId);
							failedReasons.put(failedLocalId, "Incoming record is older than the server record");
						}
					} else {
							// Idempotency guard: a gateway bill payment carries a globally-unique
							// gateway_txn_id. If WorkManager retries a push after a dropped response,
							// the retry arrives as a "new" record (fresh localId) but the row already
							// exists. Skip the insert instead of letting saveAll trip the partial
							// unique index (uq_bill_payments_gateway_txn) and fail the whole batch.
							if (incomingRecord instanceof BillPayment newBillPayment) {
								String txnId = newBillPayment.getGatewayTxnId();
								if (txnId != null && !txnId.isBlank()
										&& billPaymentRepository.existsByRestaurantIdAndGatewayTxnId(targetTenantId, txnId)) {
									log.info("Skipping duplicate gateway bill payment insert localId={} txnId={} tenantId={}",
											newBillPayment.getLocalId(), txnId, targetTenantId);
									successfulLocalIds.add(newBillPayment.getLocalId());
									continue;
								}
							}

							// Relational ID Resolution for New Records
						resolveRelationalIds(incomingRecord, idMaps);

						if (incomingRecord instanceof RestaurantProfile incomingProfile) {
							incomingProfile.setTimezone(DEFAULT_TIMEZONE);
						}

						// Refund default: new bills have no admin refund yet; default to ZERO
						// so the column is never NULL (easier for reports/aggregations).
						if (incomingRecord instanceof Bill newBill && newBill.getRefundAmount() == null) {
							newBill.setRefundAmount(java.math.BigDecimal.ZERO);
						}

						T staged = recordsToSaveMap.get(incomingRecord.getLocalId());
						if (staged == null || incomingRecord.getUpdatedAt() > staged.getUpdatedAt()) {
							recordsToSaveMap.put(incomingRecord.getLocalId(), incomingRecord);
						}
						successfulLocalIds.add(incomingRecord.getLocalId());
					}
				} catch (Exception e) {
					log.error("Sync Error for record class {}: {}", incomingRecord.getClass().getSimpleName(), e.getMessage(), e);
					if (incomingRecord.getLocalId() != null) {
						Long failedLocalId = incomingRecord.getLocalId();
						failedLocalIds.add(failedLocalId);
						failedReasons.put(failedLocalId, sanitizeFailureReason(e.getMessage()));
					}
					log.warn("Sync error staging record deviceId={} type={} error={}",
							incomingRecord.getDeviceId(), incomingRecord.getClass().getSimpleName(), e.getMessage());
				}
			}
			
			allRecordsToSave.addAll(recordsToSaveMap.values());
		}

		// Generate public_token for new bills that don't have one
		for (T record : allRecordsToSave) {
			if (record instanceof Bill bill && bill.getPublicToken() == null) {
				bill.setPublicToken(java.util.UUID.randomUUID());
			}
		}

		if (!allRecordsToSave.isEmpty()) {
			try {
				List<T> saved = repository.saveAll(allRecordsToSave);
				for (T entity : saved) {
					if (entity.getLocalId() != null && entity.getId() != null) {
						localToServerIdMap.put(entity.getLocalId(), entity.getId());
					}
				}
			} catch (DataIntegrityViolationException e) {
				// Log the specific constraint violation details so we can identify
				// which field or constraint is causing the 409 during bill push.
				String causeMessage = e.getMostSpecificCause() != null
						? e.getMostSpecificCause().getMessage()
						: e.getMessage();
				log.error("DataIntegrityViolationException during saveAll for {} records. Cause: {}",
						allRecordsToSave.size(), causeMessage);
				for (T record : allRecordsToSave) {
					if (record instanceof Bill bill) {
						log.error("  Bill: localId={} serverId={} deviceId={} restaurantId={} dailyOrderId={} lifetimeOrderId={} orderType={} subtotal={} total={} paymentMode={} paymentStatus={} orderStatus={} lastResetDate={} createdBy={}",
								bill.getLocalId(), bill.getId(), bill.getDeviceId(),
								bill.getRestaurantId(), bill.getDailyOrderId(),
								bill.getLifetimeOrderId(), bill.getOrderType(),
								bill.getSubtotal(), bill.getTotalAmount(),
								bill.getPaymentMode(), bill.getPaymentStatus(),
								bill.getOrderStatus(), bill.getLastResetDate(),
								bill.getCreatedBy());
					}
				}
				throw e;
			}
		}

		log.info("Successfully batch synced {} records for Tenant ID: {}", successfulLocalIds.size(), tenantId);

		log.info("Push sync completed tenantId={} success={} failed={} saved={}",
				tenantId, successfulLocalIds.size(), failedLocalIds.size(), allRecordsToSave.size());

		PushSyncResponse response = new PushSyncResponse(successfulLocalIds, failedLocalIds);
		response.setLocalToServerIdMap(localToServerIdMap);
		response.setFailedReasons(failedReasons);
		return response;
	}

	private String sanitizeFailureReason(String message) {
		if (message == null || message.isBlank()) {
			return "Sync rejected by server";
		}
		return message.length() > 240 ? message.substring(0, 240) : message;
	}

	private void validateBillNumberConflicts(
			Long tenantId,
			Bill incomingBill,
			com.khanabook.saas.repository.BillRepository billRepo) {
		if (Boolean.TRUE.equals(incomingBill.getIsDeleted())) {
			return;
		}
		if (incomingBill.getDeviceId() == null || incomingBill.getLocalId() == null) {
			throw new IllegalStateException("Bill identity missing. Sync again after opening Sync Center.");
		}
		if (incomingBill.getDailyOrderId() != null
				&& incomingBill.getLastResetDate() != null
				&& !incomingBill.getLastResetDate().isBlank()) {
			billRepo.findConflictingDailyOrder(
					tenantId,
					incomingBill.getLastResetDate(),
					incomingBill.getDailyOrderId(),
					incomingBill.getDeviceId(),
					incomingBill.getLocalId(),
					incomingBill.getTerminalSeries())
					.ifPresent(conflict -> {
						throw new IllegalStateException(
								"Duplicate order #" + incomingBill.getDailyOrderDisplay()
										+ " already exists for " + incomingBill.getLastResetDate()
										+ ". Resolve it in Sync Center.");
					});
		}
	}

	private boolean isTransactionalIdempotentRetry(BaseSyncEntity incoming, BaseSyncEntity existing) {
		boolean transactional = incoming instanceof Bill
				|| incoming instanceof BillItem
				|| incoming instanceof BillPayment;
		return transactional
				&& existing != null
				&& incoming.getLocalId() != null
				&& existing.getLocalId() != null
				&& incoming.getLocalId().equals(existing.getLocalId())
				&& Objects.equals(incoming.getDeviceId(), existing.getDeviceId())
				&& Objects.equals(incoming.getRestaurantId(), existing.getRestaurantId())
				&& existing.getId() != null;
	}

	private void mergeCounterState(RestaurantProfile incoming, RestaurantProfile existing) {
		Long mergedLifetime = maxNullable(existing.getLifetimeOrderCounter(), incoming.getLifetimeOrderCounter());
		incoming.setLifetimeOrderCounter(mergedLifetime);

		java.time.LocalDate existingDate = parseDate(existing.getLastResetDate(), existing.getLastResetDateProper());
		java.time.LocalDate incomingDate = parseDate(incoming.getLastResetDate(), incoming.getLastResetDateProper());

		if (existingDate == null && incomingDate == null) {
			incoming.setDailyOrderCounter(maxNullable(existing.getDailyOrderCounter(), incoming.getDailyOrderCounter()));
			incoming.setLastResetDate(existing.getLastResetDate());
			incoming.setLastResetDateProper(existing.getLastResetDateProper());
			return;
		}

		if (incomingDate == null || (existingDate != null && incomingDate.isBefore(existingDate))) {
			incoming.setDailyOrderCounter(existing.getDailyOrderCounter());
			incoming.setLastResetDate(existing.getLastResetDate());
			incoming.setLastResetDateProper(existing.getLastResetDateProper());
			return;
		}

		if (existingDate != null && incomingDate.isEqual(existingDate)) {
			incoming.setDailyOrderCounter(maxNullable(existing.getDailyOrderCounter(), incoming.getDailyOrderCounter()));
			incoming.setLastResetDate(existing.getLastResetDate());
			incoming.setLastResetDateProper(existing.getLastResetDateProper());
		}
	}

	private Long maxNullable(Long a, Long b) {
		if (a == null) return b;
		if (b == null) return a;
		return Math.max(a, b);
	}

	private java.time.LocalDate parseDate(String textDate, java.time.LocalDate properDate) {
		if (properDate != null) return properDate;
		if (textDate == null || textDate.isBlank()) return null;
		try {
			return java.time.LocalDate.parse(textDate);
		} catch (Exception ignored) {
			return null;
		}
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
				if (sl.getVariantId() != null) variantLocalIds.add(sl.getVariantId());
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
		Map<Long, Long> billLocalToServerId      = new HashMap<>();
		Map<Long, Long> menuItemLocalToServerId  = new HashMap<>();
		Map<Long, Long> variantLocalToServerId   = new HashMap<>();
		Map<Long, Long> categoryLocalToServerId  = new HashMap<>();
	}

	private void resolveRelationalIds(BaseSyncEntity record, RelationalIdMaps maps) {
		try {
			if (record instanceof MenuItem menuItem) {
				if (menuItem.getCategoryId() != null) {
					Long serverId = maps.categoryLocalToServerId.get(menuItem.getCategoryId());
					if (serverId != null) {
						menuItem.setServerCategoryId(serverId);
						menuItem.setCategoryId(serverId); // CRITICAL: Update FK column
					}
				}
			} else if (record instanceof ItemVariant variant) {
				if (variant.getMenuItemId() != null) {
					Long serverId = maps.menuItemLocalToServerId.get(variant.getMenuItemId());
					if (serverId != null) {
						variant.setServerMenuItemId(serverId);
						variant.setMenuItemId(serverId); // CRITICAL: Update FK column
					}
				}
			} else if (record instanceof BillItem billItem) {
				if (billItem.getBillId() != null) {
					Long serverId = maps.billLocalToServerId.get(billItem.getBillId());
					if (serverId != null) {
						billItem.setServerBillId(serverId);
						billItem.setBillId(serverId); // CRITICAL: Update FK column
					}
				}
				if (billItem.getMenuItemId() != null) {
					Long serverId = maps.menuItemLocalToServerId.get(billItem.getMenuItemId());
					if (serverId != null) {
						billItem.setServerMenuItemId(serverId);
						billItem.setMenuItemId(serverId); // CRITICAL: Update FK column
					}
				}
				if (billItem.getVariantId() != null) {
					Long serverId = maps.variantLocalToServerId.get(billItem.getVariantId());
					if (serverId != null) {
						billItem.setServerVariantId(serverId);
						billItem.setVariantId(serverId); // CRITICAL: Update FK column
					}
				}
			} else if (record instanceof BillPayment payment) {
				if (payment.getBillId() != null) {
					Long serverId = maps.billLocalToServerId.get(payment.getBillId());
					if (serverId != null) {
						payment.setServerBillId(serverId);
						payment.setBillId(serverId); // CRITICAL: Update FK column
					}
				}
			} else if (record instanceof StockLog logRecord) {
				if (logRecord.getMenuItemId() != null) {
					Long serverId = maps.menuItemLocalToServerId.get(logRecord.getMenuItemId());
					if (serverId != null) {
						logRecord.setServerMenuItemId(serverId);
						logRecord.setMenuItemId(serverId); // CRITICAL: Update FK column
					}
				}
				if (logRecord.getVariantId() != null) {
					Long serverId = maps.variantLocalToServerId.get(logRecord.getVariantId());
					if (serverId != null) {
						logRecord.setServerVariantId(serverId);
						logRecord.setVariantId(serverId); // CRITICAL: Update FK column
					}
				}
			}
		} catch (Exception e) {
			log.error("Resolution Failed: {}", e.getMessage(), e);
		}
	}
}
