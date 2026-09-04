package com.khanabook.saas.sync.service;

import com.khanabook.saas.utility.AppConstants;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
import com.khanabook.saas.util.BillTerminalUtil;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import com.khanabook.saas.sync.repository.SyncRepository;
import com.khanabook.saas.security.authz.OfflineAuthDecider;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.entity.StaffPermissionRevision;
import com.khanabook.saas.repository.StaffPermissionRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenericSyncService {
	private static final Logger log = LoggerFactory.getLogger(GenericSyncService.class);

	private final BillRepository billRepository;
	private final BillPaymentRepository billPaymentRepository;
	private final MenuItemRepository menuItemRepository;
	private final ItemVariantRepository itemVariantRepository;
	private final CategoryRepository categoryRepository;
	private final RestaurantTerminalRepository terminalRepository;
	private final SecurityAuditService securityAuditService;
	private final SyncFallbackSaver syncFallbackSaver;
	private final PermissionService permissionService;
	private final StaffPermissionRevisionRepository revisionRepo;
	private final RelationalIdResolver relationalIdResolver;
	private final TerminalOwnershipService terminalOwnershipService;
	private final BillSyncService billSyncService;
	private final SyncNotificationService syncNotificationService;
	private final UserProfileSyncService userProfileSyncService;
	private final BillPaymentSyncService billPaymentSyncService;

	// Optional: recipe-based raw-material inventory (V81). Absent only if the
	// migration hasn't run.
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private com.khanabook.saas.service.InventoryService inventoryService;

	// Phase C strict mode: when true, bill / bill-item / bill-payment pushes without an
	// X-Terminal-Token are rejected. KBOOK_ADMIN remains exempt. While false (rollout),
	// legacy no-token clients keep working via device-based ownership.
	@org.springframework.beans.factory.annotation.Value("${terminal.sync.strict:false}")
	private boolean terminalSyncStrict;

	/** Maximum allowed clock skew between device and server (5 minutes = 300,000 ms). */
	@org.springframework.beans.factory.annotation.Value("${sync.clock-skew.max-ms:300000}")
	private long maxClockSkewMs;

	/**
	 * Offline backlog grace in milliseconds. Records whose updatedAt is older than
	 * (serverTime - offlineGraceMs) are rejected as a broken device clock / stale
	 * replay. Records between maxClockSkewMs and offlineGraceMs behind are accepted:
	 * a device that worked offline for hours/days legitimately pushes records whose
	 * updatedAt predates the push (bill createdAt / finalizeAt). Defaults to 30 days
	 * to match the refresh-token / terminal-token lifecycle.
	 */
	@org.springframework.beans.factory.annotation.Value("${sync.clock-skew.offline-grace-ms:2592000000}")
	private long offlineGraceMs;

	private static boolean isFinalizedOrderStatus(String orderStatus) {
		return BillSyncService.isFinalizedOrderStatus(orderStatus);
	}

	private User findExistingUserByIdentity(Long tenantId, User incomingUser,
			com.khanabook.saas.repository.UserRepository userRepository) {
		return userProfileSyncService.findExistingUserByIdentity(tenantId, incomingUser, userRepository);
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
		List<Bill> newBills = new ArrayList<>();
		List<Bill> cancelledBills = new ArrayList<>();
		List<Bill> finalizedBills = new ArrayList<>();
		List<BillPayment> newPayments = new ArrayList<>();

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

		boolean payloadHasBill = payload.stream().anyMatch(r -> r instanceof Bill);
		boolean payloadHasTransactional = payload.stream()
				.anyMatch(r -> r instanceof Bill || r instanceof BillItem || r instanceof BillPayment);

		// Trusted terminal identity (from the X-Terminal-Token, not the client body).
		// We resolve and normalize the terminal here so later per-record logic treats the
		// client terminalId / terminalSeries fields as untrusted and overwrites them from
		// this context. The context is resolved whenever a terminal token is present — not
		// only for bill payloads — so child records (BillItem / BillPayment) pushed in their
		// own request are still scoped to the caller's terminal. A terminal token is only
		// *required* for bill payloads; child-only and legacy (no-token) payloads fall back
		// to device-based ownership as before.
		String trustedTerminalId = null;
		String trustedTerminalSeries = null;
		String trustedDeviceId = null;
		boolean hasTerminalContext = !isKbookAdmin
				&& (TenantContext.getCurrentTerminalId() != null || TenantContext.getCurrentTerminalSeries() != null);
		if (hasTerminalContext) {
			String authTerminalId = TenantContext.getCurrentTerminalId();
			String authTerminalSeries = TenantContext.getCurrentTerminalSeries();
			String authDeviceId = TenantContext.getCurrentTerminalDevice();
			RestaurantTerminal trustedTerminal = (authTerminalSeries != null)
					? terminalRepository.findByRestaurantIdAndTerminalSeries(tenantId, authTerminalSeries).orElse(null)
					: terminalRepository.findById(Long.valueOf(authTerminalId)).orElse(null);
			if (trustedTerminal == null) {
				securityAuditService.record("SYNC_PUSH", "TERMINAL_UNKNOWN", null, authTerminalId);
				throw new ResponseStatusException(FORBIDDEN, "Terminal is not registered for this restaurant");
			}
			if (Boolean.FALSE.equals(trustedTerminal.getIsActive())) {
				securityAuditService.record("SYNC_PUSH", "TERMINAL_DISABLED", null,
						trustedTerminal.getId() != null ? trustedTerminal.getId().toString() : authTerminalSeries);
				throw new ResponseStatusException(FORBIDDEN, "Terminal is disabled");
			}
			trustedTerminalId = trustedTerminal.getId() != null ? trustedTerminal.getId().toString()
					: trustedTerminal.getTerminalSeries();
			trustedTerminalSeries = trustedTerminal.getTerminalSeries();
			trustedDeviceId = trustedTerminal.getDeviceId() != null ? trustedTerminal.getDeviceId() : authDeviceId;
		} else if (payloadHasTransactional && !isKbookAdmin) {
			// No terminal token on a transactional payload.
			// Strict mode rejects it; otherwise legacy no-token clients keep working via
			// device-based ownership (temporary compatibility — remove after all clients ship
			// the terminal-token build).
			if (terminalSyncStrict) {
				throw new ResponseStatusException(BAD_REQUEST,
						"Terminal identity required for sync: activate a terminal and send X-Terminal-Token");
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
			RelationalIdResolver.IdMaps idMaps = relationalIdResolver.buildMaps(devicePayload, tenantId, deviceId);

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

					// Field-level validation: reject malformed/adversarial payloads early
					var validationResult = com.khanabook.saas.sync.validation.SyncPayloadValidator.validate(incomingRecord);
					if (!validationResult.valid()) {
						failedLocalIds.add(incomingRecord.getLocalId());
						failedReasons.put(incomingRecord.getLocalId(), validationResult.reason());
						continue;
					}

					if (incomingRecord instanceof Bill bill) {
						if (bill.getLastResetDate() == null || bill.getLastResetDate().isEmpty()) {
							bill.setLastResetDate(java.time.LocalDate.now().toString());
						}
						if (bill.getRefundAmount() == null) {
							bill.setRefundAmount(java.math.BigDecimal.ZERO);
						}

						// Overwrite untrusted client terminal fields from the authenticated terminal
						// context. New bills are owned by the calling terminal.
						if (trustedTerminalId != null) {
							bill.setTerminalId(trustedTerminalId);
							bill.setTerminalSeries(trustedTerminalSeries);
							bill.setCreatedDeviceId(trustedDeviceId);
							bill.setDeviceId(trustedDeviceId);
							if (bill.getCreatedTerminalId() == null || bill.getCreatedTerminalId().isBlank()) {
								bill.setCreatedTerminalId(trustedTerminalId);
							}
							if (bill.getCurrentOwnerTerminalId() == null || bill.getCurrentOwnerTerminalId().isBlank()) {
								bill.setCurrentOwnerTerminalId(trustedTerminalId);
							}
						}
						// v2 port: never leave cancel_reason NULL
						if (bill.getCancelReason() == null) {
							bill.setCancelReason("");
						}
					}

					// For KBOOK_ADMIN, ensure we use the record's restaurantId if tenantId is null
					Long targetTenantId = tenantId != null ? tenantId : incomingRecord.getRestaurantId();
					if (targetTenantId == null) {
						log.warn("Skipping record with NULL restaurantId for device: {}", deviceId);
						continue;
					}
					if (incomingRecord instanceof Bill bill
							&& repository instanceof com.khanabook.saas.repository.BillRepository billRepo) {
						billSyncService.validateBillNumberConflicts(targetTenantId, bill, billRepo);
					}

					// Idempotent bill upsert: if a bill with this publicToken already exists,
					// treat the push as a successful no-op — return the existing server ID.
					// This prevents duplicate bills when network timeouts cause client retries.
					// Only applies to NEW bills (no serverId yet) — updates must flow through
					// the normal updatedAt comparison logic below.
					if (incomingRecord instanceof Bill incomingBill
							&& incomingBill.getPublicToken() != null
							&& incomingRecord.getId() == null
							&& repository instanceof com.khanabook.saas.repository.BillRepository billRepo2) {
						var existingByToken = billRepo2.findByRestaurantIdAndPublicToken(
								targetTenantId, incomingBill.getPublicToken());
					if (existingByToken.isPresent()) {
						Bill existing = existingByToken.get();
						// Ownership guard: a publicToken replay from another terminal must NOT be
						// treated as an idempotent success — it would let one terminal silently
						// claim/overwrite another terminal's bill.
						if (!isKbookAdmin && trustedTerminalId != null) {
							String tokenOwner = BillTerminalUtil.ownerTerminalId(existing);
							boolean tokenLegacy = BillTerminalUtil.isLegacyUnresolved(existing);
							boolean legacyReclaimable = tokenLegacy && trustedDeviceId != null
									&& trustedDeviceId.equals(existing.getCreatedDeviceId());
							if (tokenOwner != null && !tokenOwner.equals(trustedTerminalId) && !legacyReclaimable) {
								securityAuditService.record("SYNC_PUSH",
										tokenLegacy ? "LEGACY_BILL_REJECTED" : "CROSS_TERMINAL_UPDATE",
										incomingBill.getPublicToken() != null
												? incomingBill.getPublicToken().toString() : null,
										tokenOwner);
								failedLocalIds.add(incomingRecord.getLocalId());
								failedReasons.put(incomingRecord.getLocalId(),
										"Bill belongs to another terminal and cannot be modified from this terminal");
								continue;
							}
						}
						// Owning terminal / admin / legacy-reclaimable: fall through to the normal
						// LWW update path (existingRecord is resolved again by publicToken below).
						// Short-circuiting here as a no-op success used to swallow legitimate
						// state transitions (e.g. draft -> completed) sent without a serverId.
					}
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

				// ── P0-1: Clock skew guard ────────────────────────────────────────
				// Two rules:
				//  1. Clock-AHEAD records (more than maxClockSkewMs in the future) are
				//     always rejected — a future-dated timestamp would win whole-record
				//     LWW forever and corrupt shared master data.
				//  2. Clock-BEHIND records are rejected only beyond an offline-grace
				//     window (offlineGraceMs). A device that worked offline for hours or
				//     days legitimately pushes records whose updatedAt predates the push.
				//     Within the grace window they are accepted — LWW still protects
				//     existing rows (an older timestamp can never overwrite newer server
				//     state) and new inserts have no server record to clobber. Beyond the
				//     grace window the device clock is presumed broken or the record is a
				//     stale replay.
				if (incomingRecord.getUpdatedAt() != null && maxClockSkewMs > 0) {
					long updatedAt = incomingRecord.getUpdatedAt();
					if (updatedAt > serverTime + maxClockSkewMs) {
						long skew = updatedAt - serverTime;
						String direction = "ahead";
						log.warn("CLOCK_SKEW_REJECTED tenantId={} device={} localId={} type={} " +
								"incoming={} server={} skew={}ms ({})",
								targetTenantId, incomingRecord.getDeviceId(), incomingRecord.getLocalId(),
								incomingRecord.getClass().getSimpleName(),
								updatedAt, serverTime, skew, direction);
						securityAuditService.record("SYNC_PUSH", "CLOCK_SKEW_REJECTED",
								String.valueOf(incomingRecord.getLocalId()),
								incomingRecord.getDeviceId());
						failedLocalIds.add(incomingRecord.getLocalId());
						failedReasons.put(incomingRecord.getLocalId(),
								"Terminal clock is " + direction + " by " + (skew / 1000) +
								" seconds. Please correct time settings on the device.");
						continue;
					}
					if (offlineGraceMs > 0 && updatedAt < serverTime - offlineGraceMs) {
						long skew = serverTime - updatedAt;
						String direction = "behind";
						log.warn("CLOCK_SKEW_REJECTED tenantId={} device={} localId={} type={} " +
								"incoming={} server={} skew={}ms ({}) beyond offline grace",
								targetTenantId, incomingRecord.getDeviceId(), incomingRecord.getLocalId(),
								incomingRecord.getClass().getSimpleName(),
								updatedAt, serverTime, skew, direction);
						securityAuditService.record("SYNC_PUSH", "CLOCK_SKEW_REJECTED",
								String.valueOf(incomingRecord.getLocalId()),
								incomingRecord.getDeviceId());
						failedLocalIds.add(incomingRecord.getLocalId());
						failedReasons.put(incomingRecord.getLocalId(),
								"Terminal clock is " + direction + " by " + (skew / 1000) +
								" seconds (older than the offline grace window). Please correct time settings on the device.");
						continue;
					}
					if (updatedAt < serverTime - maxClockSkewMs) {
						// Legitimate offline backlog within the grace window: accept, but
						// surface for ops visibility (it is NOT a rejection).
						long skew = serverTime - updatedAt;
						log.warn("CLOCK_SKEW_BEHIND_WITHIN_GRACE tenantId={} device={} localId={} type={} " +
								"incoming={} server={} skew={}ms (accepted offline backlog)",
								targetTenantId, incomingRecord.getDeviceId(), incomingRecord.getLocalId(),
								incomingRecord.getClass().getSimpleName(),
								updatedAt, serverTime, skew);
						securityAuditService.record("SYNC_PUSH", "CLOCK_SKEW_BEHIND_WITHIN_GRACE",
								String.valueOf(incomingRecord.getLocalId()),
								incomingRecord.getDeviceId());
					}
				}

				incomingRecord.setServerUpdatedAt(serverTime);
					if (incomingRecord.getIsDeleted() == null) {
						incomingRecord.setIsDeleted(false);
					}

					if (existingRecord != null) {
						if (incomingRecord instanceof Bill incomingBill && existingRecord instanceof Bill existingBill
								&& trustedTerminalId != null) {
							String owner = BillTerminalUtil.ownerTerminalId(existingBill);
							boolean legacy = BillTerminalUtil.isLegacyUnresolved(existingBill);
							if (owner != null && !owner.equals(trustedTerminalId)) {
								// A legacy (pre-terminal) bill can still be touched by the tablet that
								// originally created it, or by an admin; otherwise it is quarantined.
								boolean allowed = isKbookAdmin
										|| (legacy && trustedDeviceId != null
												&& trustedDeviceId.equals(existingBill.getCreatedDeviceId()));
								if (!allowed) {
									securityAuditService.record("SYNC_PUSH",
											legacy ? "LEGACY_BILL_REJECTED" : "CROSS_TERMINAL_UPDATE",
											incomingBill.getPublicToken() != null
													? incomingBill.getPublicToken().toString() : null,
											owner);
									failedLocalIds.add(incomingRecord.getLocalId());
									failedReasons.put(incomingRecord.getLocalId(),
											legacy ? "Legacy bill must be reassigned by admin before modification"
													: "Bill belongs to another terminal and cannot be modified from this terminal");
									continue;
								}
								// Reclaim the legacy bill to the calling terminal (original tablet / admin).
								if (legacy) {
									existingBill.setCurrentOwnerTerminalId(trustedTerminalId);
								}
							}
							incomingBill.setCreatedTerminalId(existingBill.getCreatedTerminalId());
							incomingBill.setCurrentOwnerTerminalId(existingBill.getCurrentOwnerTerminalId());
						}
						if (incomingRecord.getUpdatedAt() >= existingRecord.getUpdatedAt()) {

							// Phase 3: Stale-push conflict detection for categories, variants,
							// and profiles. When the device's serverUpdatedAt is older than the
							// server's current serverUpdatedAt, another device has already pushed
							// a newer version. Quarantine instead of silently overwriting (LWW
							// would let the stale push win by timestamp alone).
							if ((incomingRecord instanceof Category
									|| incomingRecord instanceof ItemVariant
									|| incomingRecord instanceof RestaurantProfile)
									&& existingRecord.getServerUpdatedAt() != null
									&& incomingRecord.getServerUpdatedAt() != null
									&& existingRecord.getServerUpdatedAt() > incomingRecord.getServerUpdatedAt()) {
								failedLocalIds.add(incomingRecord.getLocalId());
								failedReasons.put(incomingRecord.getLocalId(),
										"STALE_PUSH_CONFLICT: another device updated this "
										+ incomingRecord.getClass().getSimpleName()
										+ " since your last sync. Pull to see the latest version.");
								continue;
							}

								if (incomingRecord instanceof User user && existingRecord instanceof User existingUser) {
									userProfileSyncService.mergeUserFields(user, existingUser);

									// Prevent duplicate email/phone numbers from crashing the batch sync
									if (repository instanceof com.khanabook.saas.repository.UserRepository) {
										com.khanabook.saas.repository.UserRepository userRepo = (com.khanabook.saas.repository.UserRepository) repository;
										userProfileSyncService.validateIdentityUniqueness(user, existingUser, userRepo);
									}
								}
							
							// Relational ID Resolution for Updates
							relationalIdResolver.resolve(incomingRecord, idMaps);

							// Field-level merge (field-mask): when the device tells us which
							// fields it actually changed, only those are merged onto the server
							// row. Unmasked fields keep the server value, so two devices editing
							// DIFFERENT fields of the same record no longer clobber each other
							// (Phase 1 — fixes menu price/name/category silent loss). Runs before
							// preserveServerOwnedState so server-owned sticky state still wins.
							applyChangedFieldsMerge(incomingRecord, existingRecord);

							preserveServerOwnedState(incomingRecord, existingRecord);

							// Enforce parent-bill terminal ownership for child records
							// (BillItem / BillPayment) so one terminal cannot attach or mutate
							// lines against another terminal's bill.
							if (incomingRecord instanceof BillItem || incomingRecord instanceof BillPayment) {
								if (!terminalOwnershipService.isChildOwnershipAllowed(incomingRecord, targetTenantId,
										trustedTerminalId, trustedDeviceId, isKbookAdmin)) {
									failedLocalIds.add(incomingRecord.getLocalId());
									failedReasons.put(incomingRecord.getLocalId(),
											"Record references a bill owned by another terminal");
								securityAuditService.record("SYNC_PUSH", "CHILD_CROSS_TERMINAL",
										terminalOwnershipService.childParentToken(incomingRecord),
										terminalOwnershipService.childOwnerTerminal(incomingRecord, targetTenantId));
									continue;
							}
							}
							// v2 port: record cancellations + protect gateway-owned bill state
							// so an Easebuzz-settled bill is not overwritten by a stale device push.

							if (incomingRecord instanceof Bill incomingBill && existingRecord instanceof Bill existingBill) {
								if (!"cancelled".equalsIgnoreCase(existingBill.getPaymentStatus()) && "cancelled".equalsIgnoreCase(incomingBill.getPaymentStatus())) {
									cancelledBills.add(incomingBill);
								}
								if (isFinalizedOrderStatus(incomingBill.getOrderStatus())
										&& !isFinalizedOrderStatus(existingBill.getOrderStatus())) {
									finalizedBills.add(incomingBill);
								}
								// NOTE: v2 also called hasBackendGatewayPayment()/preserveGatewayOwnedBillState()
								// here, but both were unimplemented stubs in v2 (return false / empty body), so
								// the guarded branch never executed. Omitted rather than carried over as dead code.
							}

							if (incomingRecord instanceof RestaurantProfile incomingProfile
									&& existingRecord instanceof RestaurantProfile existingProfile) {
								incomingProfile.setTimezone(AppConstants.DEFAULT_TIMEZONE);
								applyProfileChangedFieldsMerge(incomingRecord, existingRecord);
								mergeCounterState(incomingProfile, existingProfile);
							}

							// Refund preservation: refundAmount is server-owned (set by admin via
							// markManualRefund). Android never sends it, so ALWAYS restore the
							// server value on updates to prevent a push from zeroing a refund.
							if (incomingRecord instanceof Bill incomingBill
									&& existingRecord instanceof Bill existingBill) {
								incomingBill.setRefundAmount(existingBill.getRefundAmount());
								if (!"cancelled".equalsIgnoreCase(existingBill.getPaymentStatus())
										&& "cancelled".equalsIgnoreCase(incomingBill.getPaymentStatus())) {
									cancelledBills.add(incomingBill);
								}
								if (isFinalizedOrderStatus(incomingBill.getOrderStatus())
										&& !isFinalizedOrderStatus(existingBill.getOrderStatus())) {
									finalizedBills.add(incomingBill);
								}
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
						} else if (billSyncService.isTransactionalIdempotentRetry(incomingRecord, existingRecord)) {
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
							// Idempotency guards for bill payments delegated to BillPaymentSyncService
							if (incomingRecord instanceof BillPayment newBillPayment) {
								var idempotencyResult = billPaymentSyncService.checkIdempotency(targetTenantId, newBillPayment);
								if (idempotencyResult.matched()) {
									if (idempotencyResult.conflictReason() != null) {
										log.error("CONFLICT: {} tenantId={}", idempotencyResult.conflictReason(), targetTenantId);
										failedLocalIds.add(newBillPayment.getLocalId());
										failedReasons.put(newBillPayment.getLocalId(), idempotencyResult.conflictReason());
										continue;
									}
									BillPayment existing = idempotencyResult.existingPayment();
									successfulLocalIds.add(newBillPayment.getLocalId());
									localToServerIdMap.put(newBillPayment.getLocalId(), existing.getId());
									continue;
								}
							}

							// Relational ID Resolution for New Records
						relationalIdResolver.resolve(incomingRecord, idMaps);

						// SECURITY: new users created via sync are always OWNER.
						// Only KBOOK_ADMIN can create admin users (via web-admin, not sync).
						if (incomingRecord instanceof User newUser) {
							userProfileSyncService.enforceNewUserRole(newUser, isKbookAdmin);
						}

							// Enforce parent-bill terminal ownership for child records
							// (BillItem / BillPayment) so one terminal cannot attach lines to
							// another terminal's bill.
							if (incomingRecord instanceof BillItem || incomingRecord instanceof BillPayment) {
								if (!terminalOwnershipService.isChildOwnershipAllowed(incomingRecord, targetTenantId,
										trustedTerminalId, trustedDeviceId, isKbookAdmin)) {
								failedLocalIds.add(incomingRecord.getLocalId());
								failedReasons.put(incomingRecord.getLocalId(),
										"Record references a bill owned by another terminal");
								securityAuditService.record("SYNC_PUSH", "CHILD_CROSS_TERMINAL",
										terminalOwnershipService.childParentToken(incomingRecord),
										terminalOwnershipService.childOwnerTerminal(incomingRecord, targetTenantId));
								continue;
							}
						}

						if (incomingRecord instanceof RestaurantProfile incomingProfile) {
							incomingProfile.setTimezone(AppConstants.DEFAULT_TIMEZONE);
						}

						// Refund default: new bills have no admin refund yet; default to ZERO
						// so the column is never NULL (easier for reports/aggregations).
						if (incomingRecord instanceof Bill newBill && newBill.getRefundAmount() == null) {
							newBill.setRefundAmount(java.math.BigDecimal.ZERO);
						}
						if (incomingRecord instanceof Bill freshBill) {
							newBills.add(freshBill);
							if (isFinalizedOrderStatus(freshBill.getOrderStatus())) {
								finalizedBills.add(freshBill);
							}
						} else if (incomingRecord instanceof BillPayment freshPayment) {
							newPayments.add(freshPayment);
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

		// ── Permission revalidation (Step 2): reject/quarantine offline-created
		// operations if the user's permission has been revoked after the op was created.
		// This is the core distributed-state guard: even if a permission is currently
		// granted, an op created when it was granted must be rejected if revoked later.
		// Decision A strict: lastRevokedRevision >= createdRevision → REJECT.
		// Generate public_token for new bills that don't have one
		for (T record : allRecordsToSave) {
			if (record instanceof Bill bill) {
				billSyncService.ensurePublicToken(bill);
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
				// saveAll is all-or-nothing: a single unique-constraint collision
				// (e.g. ux_bills_restaurant_invoice_series_active) rolls back the
				// ENTIRE batch. If we rethrow here the whole push fails and the
				// colliding bills stay isSynced=false on the device, which makes
				// Android re-push them on every cycle -> infinite 409 loop.
				// Instead, fall back to per-record saves so the non-colliding
				// records commit and only the genuinely conflicting localIds
				// land in failedLocalIds (which the client quarantines after
				// conflict recovery). This breaks the loop and preserves data.
				String causeMessage = e.getMostSpecificCause() != null
						? e.getMostSpecificCause().getMessage()
						: e.getMessage();
				log.error("DataIntegrityViolationException during saveAll for {} records; falling back to per-record save. Cause: {}",
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
					try {
						// REQUIRES_NEW: the batch failure may have aborted the outer
						// PostgreSQL transaction; per-record saves must commit in
						// fresh transactions or they all fail with "transaction aborted".
						T saved = syncFallbackSaver.saveRecord(repository, record);
						if (saved.getLocalId() != null && saved.getId() != null) {
							localToServerIdMap.put(saved.getLocalId(), saved.getId());
						}
					} catch (DataIntegrityViolationException recordEx) {
						// Idempotent recovery: if this is a Bill and the publicToken already exists,
						// treat as success (the previous push succeeded but client didn't get the response)
						if (record instanceof Bill failedBill) {
							Bill existing = billSyncService.attemptIdempotentRecovery(failedBill, record.getRestaurantId());
							if (existing != null) {
								if (record.getLocalId() != null && existing.getId() != null) {
									localToServerIdMap.put(record.getLocalId(), existing.getId());
								}
								// Don't add to failedLocalIds — this is a success
								continue;
							}
						}
						String recordCause = recordEx.getMostSpecificCause() != null
								? recordEx.getMostSpecificCause().getMessage()
								: recordEx.getMessage();
						log.warn("Per-record save failed localId={} cause={}",
								record.getLocalId(), recordCause);
						if (record.getLocalId() != null) {
							// A record may have been staged into successfulLocalIds before the
							// batch save; it must not be reported as BOTH success and failure.
							successfulLocalIds.remove(record.getLocalId());
							failedLocalIds.add(record.getLocalId());
							failedReasons.put(record.getLocalId(), sanitizeFailureReason(recordCause));
						}
					} catch (RuntimeException recordEx) {
						// The aborted-outer-transaction case and other runtime failures:
						// never report a record as success AND failure simultaneously.
						String recordCause = recordEx.getMessage();
						log.warn("Per-record save failed localId={} cause={}", record.getLocalId(), recordCause);
						if (record.getLocalId() != null) {
							successfulLocalIds.remove(record.getLocalId());
							failedLocalIds.add(record.getLocalId());
							failedReasons.put(record.getLocalId(), sanitizeFailureReason(recordCause));
						}
					}
				}
			}
		}

		log.info("Successfully batch synced {} records for Tenant ID: {}", successfulLocalIds.size(), tenantId);

		// ── Recipe-based inventory deduction ─────────────────────────────
		if (inventoryService != null && repository instanceof BillRepository && !finalizedBills.isEmpty()) {
			for (Bill bill : finalizedBills) {
				if (!successfulLocalIds.contains(bill.getLocalId())) continue;
				try {
					// Resolve the SAVED row's id: payload instances are detached after
					// merge() in saveAll, so bill.getId() may be null here.
					Long savedId = bill.getId() != null
							? bill.getId()
							: localToServerIdMap.get(bill.getLocalId());
					if (savedId == null) continue;
					Bill managedBill = billRepository.findById(savedId).orElse(null);
					if (managedBill == null) continue;
					inventoryService.deductForFinalizedBill(managedBill);
				} catch (Exception e) {
					log.warn("Inventory deduction failed for bill localId={}: {}",
							bill.getLocalId(), e.getMessage());
				}
			}
		}

		// ── Push Sync Notifications ──────────────────────────────────────
		if (repository instanceof BillRepository) {
			syncNotificationService.pushCancellationNotifications(cancelledBills, successfulLocalIds);
		}
		if (repository instanceof BillPaymentRepository) {
			syncNotificationService.pushPaymentNotifications(newPayments, successfulLocalIds);
		}
		}

		log.info("Push sync completed tenantId={} success={} failed={} saved={}",
				tenantId, successfulLocalIds.size(), failedLocalIds.size(), allRecordsToSave.size());

		PushSyncResponse response = new PushSyncResponse(successfulLocalIds, failedLocalIds);
		response.setLocalToServerIdMap(localToServerIdMap);
		response.setFailedReasons(failedReasons);
		return response;
	}

	// Methods moved to BillSyncService: isExactPaymentMatch, validateBillNumberConflicts, isTransactionalIdempotentRetry

	private String sanitizeFailureReason(String message) {
		if (message == null || message.isBlank()) {
			return "Sync rejected by server";
		}
		return message.length() > 240 ? message.substring(0, 240) : message;
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

	static void preserveServerOwnedState(BaseSyncEntity incoming, BaseSyncEntity existing) {
		if (incoming instanceof User incomingUser && existing instanceof User existingUser) {
			incomingUser.setIsActive(existingUser.getIsActive() != null ? existingUser.getIsActive() : true);
			incomingUser.setTokenInvalidatedAt(existingUser.getTokenInvalidatedAt());
			incomingUser.setRole(existingUser.getRole());
		}
		if (incoming instanceof RestaurantProfile incomingProfile
				&& existing instanceof RestaurantProfile existingProfile) {
			incomingProfile.setIsSuspended(existingProfile.getIsSuspended());
		}
		// Inventory cascade is server-owned: when a raw material runs out,
		// InventoryService hides dependent menu items. A device menu push must
		// not flip them back to available — re-enabling goes through a server
		// action (restock / owner toggle), never a client LWW push.
		if (incoming instanceof MenuItem incomingItem && existing instanceof MenuItem existingItem) {
			if (!Boolean.TRUE.equals(existingItem.getIsAvailable())) {
				incomingItem.setIsAvailable(false);
			}
		}
		if (incoming instanceof ItemVariant incomingVariant
				&& existing instanceof ItemVariant existingVariant) {
			if (!Boolean.TRUE.equals(existingVariant.getIsAvailable())) {
				incomingVariant.setIsAvailable(false);
			}
		}
		// ── P0-2: Bill state machine protection ────────────────────────────────
		// Prevent LWW from reverting finalized bill state. Once a bill reaches
		// a terminal state (paid, completed, cancelled), a stale device push
		// with a higher timestamp must not undo the transition.
		if (incoming instanceof Bill incomingBill && existing instanceof Bill existingBill) {
			// paymentStatus: "paid" is terminal. Gateway webhook sets it.
			// A stale device push must not revert paid → pending.
			if ("paid".equalsIgnoreCase(existingBill.getPaymentStatus())
					&& !"paid".equalsIgnoreCase(incomingBill.getPaymentStatus())) {
				incomingBill.setPaymentStatus(existingBill.getPaymentStatus());
				incomingBill.setPaidAt(existingBill.getPaidAt());
				incomingBill.setGatewayTxnId(existingBill.getGatewayTxnId());
				incomingBill.setGatewayStatus(existingBill.getGatewayStatus());
			}
			// orderStatus: completed/paid/cancelled are terminal.
			// A stale device push must not revert completed → draft.
			if (isFinalizedOrderStatus(existingBill.getOrderStatus())
					&& !isFinalizedOrderStatus(incomingBill.getOrderStatus())) {
				incomingBill.setOrderStatus(existingBill.getOrderStatus());
			}
			// cancelled is also terminal — don't un-cancel
			if ("cancelled".equalsIgnoreCase(existingBill.getOrderStatus())
					&& !"cancelled".equalsIgnoreCase(incomingBill.getOrderStatus())) {
				incomingBill.setOrderStatus(existingBill.getOrderStatus());
			}
		}
	}

	/**
	 * Field-level merge based on the device's declared field-mask (Phase 1).
	 *
	 * <p>Only {@link MenuItem} supports it today. The incoming record carries a
	 * comma-separated {@code changedFields} list naming the menu fields the device
	 * actually edited. For every field NOT in the mask, the server value on the
	 * existing row is restored onto the incoming (winning) record — so two devices
	 * editing DIFFERENT fields of the same item no longer clobber each other.
	 *
	 * <p>Merge semantics per field:
	 * <ul>
	 *   <li>{@code basePrice} — price edit only touches price</li>
	 *   <li>{@code name} / {@code description} — text edit only</li>
	 *   <li>{@code serverCategoryId} / {@code categoryId} — category reassignment</li>
	 *   <li>{@code isAvailable} — availability toggle (still subject to the
	 *       inventory-stickiness carve-out in preserveServerOwnedState)</li>
	 *   <li>{@code isDeleted} — soft delete</li>
	 *   <li>Everything else (foodType, barcode, sortOrder, stock fields) — gated
	 *       behind {@code *} wildcard so unmasked values are preserved.</li>
	 * </ul>
	 *
	 * <p>Null/blank {@code changedFields} = legacy whole-record LWW (older clients).
	 * {@code "all"} = brand-new record or full overwrite — nothing restored.
	 */
	private void applyChangedFieldsMerge(BaseSyncEntity incoming, BaseSyncEntity existing) {
		if (!(incoming instanceof MenuItem incomingItem && existing instanceof MenuItem existingItem)) {
			return;
		}
		String mask = incomingItem.getChangedFields();
		if (mask == null || mask.isBlank() || "all".equalsIgnoreCase(mask.trim())) {
			return;
		}
		java.util.Set<String> changed = new java.util.HashSet<>();
		for (String f : mask.split(",")) {
			String t = f == null ? "" : f.trim();
			if (!t.isEmpty() && !"*".equals(t)) {
				changed.add(t.toLowerCase());
			}
		}
		boolean wildcard = mask.contains("*");
		if (!changed.contains("baseprice")) incomingItem.setBasePrice(existingItem.getBasePrice());
		if (!changed.contains("name")) incomingItem.setName(existingItem.getName());
		if (!changed.contains("description")) incomingItem.setDescription(existingItem.getDescription());
		if (!changed.contains("servercategoryid")) incomingItem.setServerCategoryId(existingItem.getServerCategoryId());
		if (!changed.contains("categoryid")) incomingItem.setCategoryId(existingItem.getCategoryId());
		if (!changed.contains("isavailable")) incomingItem.setIsAvailable(existingItem.getIsAvailable());
		if (!changed.contains("isdeleted")) incomingItem.setIsDeleted(existingItem.getIsDeleted());
		if (!wildcard) {
			if (!changed.contains("foodtype")) incomingItem.setFoodType(existingItem.getFoodType());
			if (!changed.contains("barcode")) incomingItem.setBarcode(existingItem.getBarcode());
			if (!changed.contains("currentstock")) incomingItem.setCurrentStock(existingItem.getCurrentStock());
			if (!changed.contains("lowstockthreshold")) incomingItem.setLowStockThreshold(existingItem.getLowStockThreshold());
		}
	}

	/**
	 * Field-level merge for {@link RestaurantProfile} (Phase 2).
	 * Same mask protocol as {@link #applyChangedFieldsMerge}. Restores server values
	 * for profile fields NOT in the device's changed set. Server-owned fields
	 * (isSuspended, counters) are always restored via {@link #preserveServerOwnedState}.
	 */
	private void applyProfileChangedFieldsMerge(BaseSyncEntity incoming, BaseSyncEntity existing) {
		if (!(incoming instanceof RestaurantProfile incomingP && existing instanceof RestaurantProfile existingP)) {
			return;
		}
		String mask = incomingP.getChangedFields();
		if (mask == null || mask.isBlank() || "all".equalsIgnoreCase(mask.trim())) {
			return;
		}
		java.util.Set<String> changed = new java.util.HashSet<>();
		for (String f : mask.split(",")) {
			String t = f == null ? "" : f.trim();
			if (!t.isEmpty() && !"*".equals(t)) {
				changed.add(t.toLowerCase());
			}
		}
		boolean wildcard = mask.contains("*");
		// Business info
		if (!changed.contains("shopname")) incomingP.setShopName(existingP.getShopName());
		if (!changed.contains("shopaddress")) incomingP.setShopAddress(existingP.getShopAddress());
		if (!changed.contains("whatsappnumber")) incomingP.setWhatsappNumber(existingP.getWhatsappNumber());
		if (!changed.contains("email")) incomingP.setEmail(existingP.getEmail());
		if (!changed.contains("fssainumber")) incomingP.setFssaiNumber(existingP.getFssaiNumber());
		if (!changed.contains("fssaiexpirydate")) incomingP.setFssaiExpiryDate(existingP.getFssaiExpiryDate());
		if (!changed.contains("country")) incomingP.setCountry(existingP.getCountry());
		if (!changed.contains("currency")) incomingP.setCurrency(existingP.getCurrency());
		if (!changed.contains("timezone")) incomingP.setTimezone(existingP.getTimezone());
		if (!changed.contains("logopath")) incomingP.setLogoPath(existingP.getLogoPath());
		if (!changed.contains("logourl")) incomingP.setLogoUrl(existingP.getLogoUrl());
		if (!changed.contains("logoversion")) incomingP.setLogoVersion(existingP.getLogoVersion());
		if (!changed.contains("customwelcomemessage")) incomingP.setCustomWelcomeMessage(existingP.getCustomWelcomeMessage());
		if (!changed.contains("customfssaicomessage")) incomingP.setCustomFssaiMessage(existingP.getCustomFssaiMessage());
		if (!changed.contains("reviewurl")) incomingP.setReviewUrl(existingP.getReviewUrl());
		if (!changed.contains("invoicefooter")) incomingP.setInvoiceFooter(existingP.getInvoiceFooter());
		if (!changed.contains("showbranding")) incomingP.setShowBranding(existingP.getShowBranding());
		if (!changed.contains("maskcustomerphone")) incomingP.setMaskCustomerPhone(existingP.getMaskCustomerPhone());
		if (!changed.contains("printcustomerwhatsapp")) incomingP.setPrintCustomerWhatsapp(existingP.getPrintCustomerWhatsapp());
		if (!changed.contains("emailinvoiceconsent")) incomingP.setEmailInvoiceConsent(existingP.getEmailInvoiceConsent());
		if (!changed.contains("sessiontimeoutminutes")) incomingP.setSessionTimeoutMinutes(existingP.getSessionTimeoutMinutes());
		if (!changed.contains("orderpaymentflowmode")) incomingP.setOrderPaymentFlowMode(existingP.getOrderPaymentFlowMode());
		// GST group
		if (!changed.contains("gstenabled")) incomingP.setGstEnabled(existingP.getGstEnabled());
		if (!changed.contains("gstin")) incomingP.setGstin(existingP.getGstin());
		if (!changed.contains("istaxinclusive")) incomingP.setIsTaxInclusive(existingP.getIsTaxInclusive());
		if (!changed.contains("gstpercentage")) incomingP.setGstPercentage(existingP.getGstPercentage());
		if (!changed.contains("customtaxname")) incomingP.setCustomTaxName(existingP.getCustomTaxName());
		if (!changed.contains("customtaxnumber")) incomingP.setCustomTaxNumber(existingP.getCustomTaxNumber());
		if (!changed.contains("customtaxpercentage")) incomingP.setCustomTaxPercentage(existingP.getCustomTaxPercentage());
		if (!changed.contains("gstexpirydate")) incomingP.setGstExpiryDate(existingP.getGstExpiryDate());
		if (!changed.contains("easebuzzenabled")) incomingP.setEasebuzzEnabled(existingP.getEasebuzzEnabled());
		// UPI/payment group
		if (!changed.contains("upienabled")) incomingP.setUpiEnabled(existingP.getUpiEnabled());
		if (!changed.contains("upihandle")) incomingP.setUpiHandle(existingP.getUpiHandle());
		if (!changed.contains("upimobile")) incomingP.setUpiMobile(existingP.getUpiMobile());
		if (!changed.contains("upiqrpath")) incomingP.setUpiQrPath(existingP.getUpiQrPath());
		if (!changed.contains("upiqrurl")) incomingP.setUpiQrUrl(existingP.getUpiQrUrl());
		if (!changed.contains("upiqrversion")) incomingP.setUpiQrVersion(existingP.getUpiQrVersion());
		if (!changed.contains("cashenabled")) incomingP.setCashEnabled(existingP.getCashEnabled());
		if (!changed.contains("posenabled")) incomingP.setPosEnabled(existingP.getPosEnabled());
		// Printer group
		if (!changed.contains("printerenabled")) incomingP.setPrinterEnabled(existingP.getPrinterEnabled());
		if (!changed.contains("printername")) incomingP.setPrinterName(existingP.getPrinterName());
		if (!changed.contains("printermac")) incomingP.setPrinterMac(existingP.getPrinterMac());
		if (!changed.contains("papersize")) incomingP.setPaperSize(existingP.getPaperSize());
		if (!changed.contains("autoprintonsuccess")) incomingP.setAutoPrintOnSuccess(existingP.getAutoPrintOnSuccess());
		if (!changed.contains("includelogoInprint")) incomingP.setIncludeLogoInPrint(existingP.getIncludeLogoInPrint());
		if (!changed.contains("kitchenprinterenabled")) incomingP.setKitchenPrinterEnabled(existingP.getKitchenPrinterEnabled());
		if (!changed.contains("kitchenprintername")) incomingP.setKitchenPrinterName(existingP.getKitchenPrinterName());
		if (!changed.contains("kitchenprintermac")) incomingP.setKitchenPrinterMac(existingP.getKitchenPrinterMac());
		if (!changed.contains("kitchenprinterpapersize")) incomingP.setKitchenPrinterPaperSize(existingP.getKitchenPrinterPaperSize());
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

}
