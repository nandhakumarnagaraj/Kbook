package com.khanabook.saas.service;

import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.utility.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central service for all terminal lifecycle operations. Every path that can result
 * in an active terminal MUST go through this service to enforce the 5-terminal limit
 * atomically using a pessimistic lock on the restaurant profile row.
 */
@Service
@RequiredArgsConstructor
public class TerminalManagementService {

    private static final Logger log = LoggerFactory.getLogger(TerminalManagementService.class);
    private static final int MAX_ACTIVE_TERMINALS = 5;

    /** Number-matching recovery challenge: lifetime and wrong-attempt cap. */
    private static final long CHALLENGE_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_CHALLENGE_ATTEMPTS = 3;
    private static final java.security.SecureRandom CHALLENGE_RANDOM = new java.security.SecureRandom();

    /** Outcome of validating a submitted number-matching challenge. */
    public enum ChallengeResult { OK, MISMATCH, EXPIRED, LOCKED }

    /** Generates a two-digit challenge in the inclusive range 01–99. */
    private static String generateChallenge() {
        return String.format("%02d", CHALLENGE_RANDOM.nextInt(99) + 1);
    }

    private final RestaurantTerminalRepository terminalRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final DeviceRegistrationRequestRepository requestRepository;
    private final JwtUtility jwtUtility;
    private final SecurityAuditService securityAuditService;
    private final com.khanabook.saas.repository.UserRepository userRepository;

    // Optional: push notifications are disabled when Firebase isn't configured.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PushNotificationService pushNotificationService;

    // ── Primary designation ─────────────────────────────────────────────────────

    /**
     * Designates a terminal as the restaurant's single primary. Atomic swap under
     * the restaurant-profile pessimistic lock: clears the current primary first,
     * then sets the new one (satisfies the partial unique index backstop).
     */
    @Transactional
    public RestaurantTerminal setPrimaryTerminal(Long terminalId, Long restaurantId) {
        restaurantProfileRepository.findAndLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Terminal not found"));

        if (!"ACTIVE".equals(terminal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_NOT_ACTIVE");
        }
        if (Boolean.TRUE.equals(terminal.getIsPrimary())) {
            return terminal;
        }

        clearPrimary(restaurantId);
        terminal.setIsPrimary(true);
        terminal.setUpdatedAt(System.currentTimeMillis());
        terminal = terminalRepository.saveAndFlush(terminal);

        securityAuditService.record("TERMINAL_PRIMARY_SET", "SUCCESS",
                terminal.getTerminalSeries(), terminal.getDeviceId());

        notifyOwners(restaurantId, "Primary Terminal Updated",
                (terminal.getTerminalName() != null ? terminal.getTerminalName() : "Terminal "
                        + terminal.getTerminalSeries())
                        + " is now your primary device.",
                String.valueOf(terminal.getId()));

        log.info("Primary terminal set: restaurant={} series={}", restaurantId, terminal.getTerminalSeries());
        return terminal;
    }

    /** Clears the current primary flag for the restaurant. Caller must hold the profile lock. */
    private void clearPrimary(Long restaurantId) {
        terminalRepository.findByRestaurantIdAndIsPrimaryTrue(restaurantId).ifPresent(current -> {
            current.setIsPrimary(false);
            current.setUpdatedAt(System.currentTimeMillis());
            terminalRepository.save(current);
        });
    }

    /**
     * Assigns primary to {@code candidate} if no ACTIVE primary exists yet
     * (first-terminal bootstrap / post-deactivation promotion fallback).
     * Caller must hold the profile lock when racing with activation paths.
     */
    private void ensurePrimaryAssigned(Long restaurantId, RestaurantTerminal candidate) {
        if (!"ACTIVE".equals(candidate.getStatus())) return;
        boolean hasPrimary = terminalRepository.findByRestaurantIdAndIsPrimaryTrue(restaurantId)
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .isPresent();
        if (!hasPrimary) {
            candidate.setIsPrimary(true);
            candidate.setUpdatedAt(System.currentTimeMillis());
            terminalRepository.save(candidate);
        }
    }

    /**
     * Promotes the oldest ACTIVE terminal to primary after the current primary
     * left the ACTIVE state. No-op when another primary already exists or when
     * no candidates remain.
     */
    private void promoteNextPrimary(Long restaurantId) {
        boolean hasPrimary = terminalRepository.findByRestaurantIdAndIsPrimaryTrue(restaurantId)
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .isPresent();
        if (hasPrimary) return;
        List<RestaurantTerminal> actives =
                terminalRepository.findByRestaurantIdAndStatusOrderByIdAsc(restaurantId, "ACTIVE");
        actives.stream().findFirst().ifPresent(next -> {
            next.setIsPrimary(true);
            next.setUpdatedAt(System.currentTimeMillis());
            terminalRepository.save(next);
            log.info("Promoted terminal {} to primary for restaurant {}",
                    next.getTerminalSeries(), restaurantId);
        });
    }

    // ── Request creation ────────────────────────────────────────────────────────

    /** Cooldown after rejection: 5 minutes before a new request can be created */
    private static final long REJECTION_COOLDOWN_MS = 5 * 60 * 1000L;

    /**
     * Creates or reuses a PENDING device registration request. Does NOT create a terminal,
     * allocate a series, or issue credentials.
     *
     * Deduplication rules:
     * - If a PENDING request already exists for this device+restaurant, returns it (idempotent).
     * - If the most recent request for this device was REJECTED within the cooldown period, returns null.
     * - Otherwise creates a new PENDING request.
     *
     * @return the pending request, or null if in rejection cooldown
     */
    @Transactional
    public DeviceRegistrationRequest createOrReuseRegistrationRequest(
            Long restaurantId, Long userId, String deviceId, String deviceModel, String requestType,
            Long matchedTerminalId) {

        // Idempotent: reuse existing pending request
        var existing = requestRepository.findByRestaurantIdAndDeviceIdAndStatus(restaurantId, deviceId, "PENDING");
        if (existing.isPresent()) {
            DeviceRegistrationRequest req = existing.get();
            // Refresh an expired/missing challenge so the device always has a live number.
            if (req.getChallengeCode() == null || req.getChallengeExpiresAt() == null
                    || System.currentTimeMillis() > req.getChallengeExpiresAt()) {
                req.setChallengeCode(generateChallenge());
                req.setChallengeExpiresAt(System.currentTimeMillis() + CHALLENGE_TTL_MS);
                req.setChallengeAttempts(0);
                req = requestRepository.save(req);
            }
            return req;
        }

        // Rejection cooldown: check if a recent rejection exists for this device
        var recentRejection = requestRepository.findMostRecentByRestaurantIdAndDeviceId(restaurantId, deviceId);
        if (recentRejection.isPresent()) {
            DeviceRegistrationRequest last = recentRejection.get();
            if ("REJECTED".equals(last.getStatus())) {
                long elapsed = System.currentTimeMillis() - (last.getProcessedAt() != null ? last.getProcessedAt() : last.getRequestedAt());
                if (elapsed < REJECTION_COOLDOWN_MS) {
                    log.info("Rejection cooldown active for device={} restaurant={} ({}ms remaining)",
                            deviceId, restaurantId, REJECTION_COOLDOWN_MS - elapsed);
                    return null; // Cooldown in effect
                }
            }
        }

        long now = System.currentTimeMillis();
        DeviceRegistrationRequest request = new DeviceRegistrationRequest();
        request.setRestaurantId(restaurantId);
        request.setRequestedByUserId(userId);
        request.setDeviceId(deviceId);
        request.setDeviceModel(deviceModel);
        request.setRequestType(requestType != null ? requestType : "NEW_DEVICE");
        request.setStatus("PENDING");
        request.setMatchedTerminalId(matchedTerminalId);
        request.setRequestedAt(now);
        request.setCreatedAt(now);
        request.setChallengeCode(generateChallenge());
        request.setChallengeExpiresAt(now + CHALLENGE_TTL_MS);
        request.setChallengeAttempts(0);
        DeviceRegistrationRequest saved = requestRepository.save(request);

        notifyOwners(restaurantId, "New Device Request",
                "A device (" + deviceModel + ") wants to join your restaurant. "
                        + "Approve it from Settings → Terminals.",
                String.valueOf(saved.getId()));

        return saved;
    }

    /**
     * Legacy method name for backward compatibility with tests.
     */
    @Transactional
    public DeviceRegistrationRequest createRegistrationRequest(
            Long restaurantId, Long userId, String deviceId, String deviceModel, String requestType,
            Long matchedTerminalId) {
        return createOrReuseRegistrationRequest(restaurantId, userId, deviceId, deviceModel, requestType, matchedTerminalId);
    }

    // ── Request status query ────────────────────────────────────────────────────

    /**
     * Returns the status of a device registration request for polling.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<DeviceRegistrationRequest> getRequestStatus(Long requestId, Long restaurantId) {
        return requestRepository.findById(requestId)
                .filter(r -> r.getRestaurantId().equals(restaurantId));
    }

    // ── Number-matching challenge ─────────────────────────────────────────────────

    /**
     * Validates a number-matching challenge submitted by the approver against the
     * value shown on the requesting device. Returns a result instead of throwing so
     * that a wrong-attempt increment (and lockout after {@link #MAX_CHALLENGE_ATTEMPTS})
     * is persisted; throwing would roll the increment back.
     *
     * Legacy rows created before challenges were introduced remain approvable when
     * both challenge fields are absent. Any request with challenge state must match.
     *
     * Lockout is PERSISTENT for the life of the request: once
     * {@link #MAX_CHALLENGE_ATTEMPTS} wrong submissions are recorded, LOCKED is
     * returned forever and the challenge fields are intentionally KEPT non-null —
     * clearing them would make the row look like a legacy row and bypass the cap
     * on the very next call. The only recovery path is the device re-requesting
     * (createOrReuseRegistrationRequest refreshes the challenge and resets attempts
     * once the old one has expired).
     */
    @Transactional
    public ChallengeResult verifyChallenge(Long requestId, Long restaurantId, String submitted) {
        DeviceRegistrationRequest request = requestRepository.findByIdWithLock(requestId)
                .filter(r -> r.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        long now = System.currentTimeMillis();
        if (request.getChallengeCode() == null && request.getChallengeExpiresAt() == null) {
            return ChallengeResult.OK;
        }

        // Lockout check BEFORE expiry so a locked request reports LOCKED (not merely
        // EXPIRED) and can never be approved without a fresh challenge cycle.
        int attempts = request.getChallengeAttempts() == null ? 0 : request.getChallengeAttempts();
        if (attempts >= MAX_CHALLENGE_ATTEMPTS) {
            securityAuditService.record("TERMINAL_APPROVE_CHALLENGE_LOCKED", "BLOCKED",
                    null, request.getDeviceId());
            return ChallengeResult.LOCKED;
        }

        if (request.getChallengeCode() == null || request.getChallengeExpiresAt() == null
                || now > request.getChallengeExpiresAt()) {
            return ChallengeResult.EXPIRED;
        }

        if (!request.getChallengeCode().equals(submitted == null ? null : submitted.trim())) {
            int next = attempts + 1;
            request.setChallengeAttempts(next);
            // Deliberately do NOT clear challengeCode/challengeExpiresAt here: a
            // cleared row would match the legacy branch above and unlock approval.
            requestRepository.save(request);
            securityAuditService.record("TERMINAL_APPROVE_CHALLENGE_MISMATCH", "BLOCKED",
                    null, request.getDeviceId());
            return next >= MAX_CHALLENGE_ATTEMPTS ? ChallengeResult.LOCKED : ChallengeResult.MISMATCH;
        }

        return ChallengeResult.OK;
    }

    // ── Approval ────────────────────────────────────────────────────────────────

    /**
     * Approves a pending request and atomically creates/activates a terminal.
     * Uses pessimistic locking on the restaurant profile to serialize concurrent approvals.
     *
     * @return the activated terminal with a fresh token
     * @throws ResponseStatusException CONFLICT if 5-terminal limit reached
     * @throws ResponseStatusException NOT_FOUND if request doesn't exist or wrong restaurant
     * @throws ResponseStatusException CONFLICT if request already processed
     */
    @Transactional
    public ActivationResult approveRequest(Long requestId, Long restaurantId, Long approvedByUserId, String role) {
        // 1. Lock the request
        DeviceRegistrationRequest request = requestRepository.findByIdWithLock(requestId)
                .filter(r -> r.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_REQUEST_ALREADY_PROCESSED");
        }

        // 2. Lock the restaurant profile (serializes concurrent approvals)
        restaurantProfileRepository.findAndLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        // 3. Determine operation type
        boolean isRecovery = "RECOVERY".equals(request.getRequestType()) || "REPLACEMENT".equals(request.getRequestType());
        RestaurantTerminal terminal;

        if (isRecovery && request.getMatchedTerminalId() != null) {
            // Recovery: rebind existing terminal
            terminal = terminalRepository.findById(request.getMatchedTerminalId())
                    .filter(t -> t.getRestaurantId().equals(restaurantId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matched terminal not found"));

            // If transitioning from non-ACTIVE to ACTIVE, enforce the limit
            if (!"ACTIVE".equals(terminal.getStatus())) {
                long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
                if (activeCount >= MAX_ACTIVE_TERMINALS) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_LIMIT_REACHED");
                }
            }

            terminal.setDeviceId(request.getDeviceId());
            terminal.setStatus("ACTIVE");
            terminal.setIsActive(true);
            terminal.setCredentialVersion(terminal.getCredentialVersion() + 1); // revokes old tokens
            terminal.setUpdatedAt(System.currentTimeMillis());
            ensurePrimaryAssigned(restaurantId, terminal);
            terminal = terminalRepository.save(terminal);
        } else {
            // New terminal: check 5-terminal limit
            long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
            if (activeCount >= MAX_ACTIVE_TERMINALS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_LIMIT_REACHED");
            }

            // Allocate series
            String series = allocateNextSeries(restaurantId);
            long now = System.currentTimeMillis();
            terminal = new RestaurantTerminal();
            terminal.setRestaurantId(restaurantId);
            terminal.setTerminalSeries(series);
            terminal.setTerminalName("Terminal " + series);
            terminal.setDeviceId(request.getDeviceId());
            terminal.setIsActive(true);
            terminal.setStatus("ACTIVE");
            terminal.setCredentialVersion(1L);
            terminal.setCreatedAt(now);
            terminal.setUpdatedAt(now);
            terminal = terminalRepository.save(terminal);
            // First terminal for the restaurant becomes primary automatically.
            ensurePrimaryAssigned(restaurantId, terminal);
        }

        // 4. Mark request approved
        request.setStatus("APPROVED");
        request.setAssignedTerminalId(terminal.getId());
        request.setProcessedAt(System.currentTimeMillis());
        request.setProcessedByUserId(approvedByUserId);
        requestRepository.save(request);

        // 5. Issue terminal token
        String terminalId = terminal.getId().toString();
        String token = jwtUtility.generateTerminalToken(
                terminal.getTerminalSeries(), restaurantId, role,
                terminalId, terminal.getTerminalSeries(), terminal.getDeviceId(),
                terminal.getCredentialVersion());

        // 6. Audit
        securityAuditService.record("TERMINAL_APPROVED", "SUCCESS",
                terminal.getTerminalSeries(), request.getDeviceId());

        log.info("Terminal approved: restaurant={} series={} type={} approvedBy={}",
                restaurantId, terminal.getTerminalSeries(), request.getRequestType(), approvedByUserId);

        notifyOwners(restaurantId, "Terminal Activated",
                (terminal.getTerminalName() != null ? terminal.getTerminalName() : "Terminal "
                        + terminal.getTerminalSeries())
                        + " is now active for your restaurant.",
                String.valueOf(terminal.getId()));

        return new ActivationResult(terminal, token);
    }

    // ── Rejection ───────────────────────────────────────────────────────────────

    @Transactional
    public void rejectRequest(Long requestId, Long restaurantId, Long rejectedByUserId, String reason) {
        DeviceRegistrationRequest request = requestRepository.findByIdWithLock(requestId)
                .filter(r -> r.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_REQUEST_ALREADY_PROCESSED");
        }

        request.setStatus("REJECTED");
        request.setProcessedAt(System.currentTimeMillis());
        request.setProcessedByUserId(rejectedByUserId);
        request.setRejectionReason(reason);
        requestRepository.save(request);

        securityAuditService.record("TERMINAL_REJECTED", "SUCCESS", null, request.getDeviceId());
    }

    // ── Deactivation ────────────────────────────────────────────────────────────

    @Transactional
    public void deactivateTerminal(Long terminalId, Long restaurantId) {
        // Hold the profile lock so primary promotion cannot race with approvals/recovery
        restaurantProfileRepository.findAndLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Terminal not found"));

        if (!"ACTIVE".equals(terminal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_ALREADY_INACTIVE");
        }

        terminal.setStatus("INACTIVE");
        terminal.setIsActive(false);
        if (Boolean.TRUE.equals(terminal.getIsPrimary())) {
            terminal.setIsPrimary(false);
        }
        terminal.setCredentialVersion(terminal.getCredentialVersion() + 1); // revokes old tokens
        terminal.setUpdatedAt(System.currentTimeMillis());
        terminalRepository.save(terminal);

        promoteNextPrimary(restaurantId);

        securityAuditService.record("TERMINAL_DEACTIVATED", "SUCCESS",
                terminal.getTerminalSeries(), terminal.getDeviceId());

        notifyOwners(restaurantId, "Terminal Deactivated",
                (terminal.getTerminalName() != null ? terminal.getTerminalName() : "Terminal "
                        + terminal.getTerminalSeries())
                        + " was deactivated. It can no longer sync data.",
                String.valueOf(terminal.getId()));
    }

    // ── Recovery (same logical terminal, new device binding) ─────────────────────

    /**
     * Validates whether a terminal is eligible for recovery/reclaim.
     *
     * @return null if eligible, or an error reason string if not eligible
     */
    public String validateRecoveryEligibility(RestaurantTerminal terminal) {
        if (terminal == null) return "Terminal not found";
        return switch (terminal.getStatus()) {
            case "ACTIVE" -> null; // Eligible: replacement/rebind of an active terminal
            case "INACTIVE" -> null; // Eligible: reactivating a deactivated terminal
            case "RECOVERY_REQUIRED" -> null; // Eligible: intended recovery state
            case "REVOKED" -> "Terminal has been permanently revoked and cannot be recovered";
            case "REPLACED" -> "Terminal has been replaced and cannot be recovered";
            default -> "Terminal is in an unrecoverable state: " + terminal.getStatus();
        };
    }

    /**
     * Recovers a terminal to a new device. Idempotent: if the terminal is already bound
     * to the same deviceId and is ACTIVE, returns without incrementing credentialVersion.
     *
     * State transition rules:
     *   ACTIVE → ACTIVE (new device): replacement/rebind; active count unchanged.
     *   INACTIVE → ACTIVE: reactivation; active count increases — requires limit check.
     *   RECOVERY_REQUIRED → ACTIVE: reactivation; active count increases — requires limit check.
     */
    @Transactional
    public ActivationResult recoverTerminal(Long terminalId, Long restaurantId, String newDeviceId, String role) {
        // Lock restaurant to prevent concurrent recovery + approval racing
        restaurantProfileRepository.findAndLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Terminal not found"));

        // Check recovery eligibility
        String ineligibleReason = validateRecoveryEligibility(terminal);
        if (ineligibleReason != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ineligibleReason);
        }

        // Idempotent: if already bound to this device and ACTIVE, don't rotate again
        boolean alreadyBound = newDeviceId.equals(terminal.getDeviceId()) && "ACTIVE".equals(terminal.getStatus());
        if (!alreadyBound) {
            // Determine if this transition increases the active count
            boolean increasesActiveCount = !"ACTIVE".equals(terminal.getStatus());

            if (increasesActiveCount) {
                // Enforce five-terminal limit: count current ACTIVE terminals
                long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
                if (activeCount >= MAX_ACTIVE_TERMINALS) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_LIMIT_REACHED");
                }
            }

            terminal.setDeviceId(newDeviceId);
            terminal.setStatus("ACTIVE");
            terminal.setIsActive(true);
            terminal.setCredentialVersion(terminal.getCredentialVersion() + 1);
            terminal.setUpdatedAt(System.currentTimeMillis());
            terminal = terminalRepository.save(terminal);
            // Reactivation may have left the restaurant without an ACTIVE primary.
            ensurePrimaryAssigned(restaurantId, terminal);

            securityAuditService.record("TERMINAL_RECOVERED", "SUCCESS",
                    terminal.getTerminalSeries(), newDeviceId);

            notifyOwners(restaurantId, "Terminal Recovered",
                    (terminal.getTerminalName() != null ? terminal.getTerminalName() : "Terminal "
                            + terminal.getTerminalSeries())
                            + " was recovered and bound to a replacement device.",
                    String.valueOf(terminal.getId()));
        }

        String tid = terminal.getId().toString();
        String token = jwtUtility.generateTerminalToken(
                terminal.getTerminalSeries(), restaurantId, role,
                tid, terminal.getTerminalSeries(), newDeviceId,
                terminal.getCredentialVersion());

        return new ActivationResult(terminal, token);
    }

    // ── Rename ──────────────────────────────────────────────────────────────────

    @Transactional
    public RestaurantTerminal renameTerminal(Long terminalId, Long restaurantId, String newName) {
        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Terminal not found"));

        terminal.setTerminalName(newName);
        terminal.setUpdatedAt(System.currentTimeMillis());
        return terminalRepository.save(terminal);
    }

    // ── Restaurant lock (exposed for TerminalController first-device atomicity) ──

    /**
     * Acquires a pessimistic write-lock on the restaurant profile row.
     * Used by TerminalController to serialize first-terminal initialization.
     */
    @Transactional
    public java.util.Optional<com.khanabook.saas.entity.RestaurantProfile> lockRestaurantForTerminalOp(Long restaurantId) {
        return restaurantProfileRepository.findAndLockByRestaurantId(restaurantId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String allocateNextSeries(Long restaurantId) {
        List<RestaurantTerminal> all = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        Set<String> assigned = new HashSet<>();
        for (RestaurantTerminal t : all) {
            if (t.getTerminalSeries() != null && !t.getTerminalSeries().isBlank()) {
                assigned.add(t.getTerminalSeries().toUpperCase());
            }
        }
        int n = 1;
        while (true) {
            String candidate = n <= 26 ? String.valueOf((char) ('A' + n - 1)) : "T" + n;
            if (!assigned.contains(candidate)) return candidate;
            n++;
        }
    }

    // ── Result record ───────────────────────────────────────────────────────────

    public record ActivationResult(RestaurantTerminal terminal, String terminalToken) {
    }
    // ── Notifications & heartbeat ─────────────────────────────────────────────

    /** Push a terminal event to the restaurant's owner devices. Never throws. */
    private void notifyOwners(Long restaurantId, String title, String message, String referenceId) {
        if (pushNotificationService == null) return;
        try {
            var owners = userRepository.findByRestaurantIdAndRoleAndIsDeletedFalse(
                    restaurantId, com.khanabook.saas.entity.UserRole.OWNER);
            java.util.List<Long> ownerIds = owners.stream()
                    .map(com.khanabook.saas.entity.User::getId).toList();
            pushNotificationService.pushToUsers(restaurantId, ownerIds,
                    title, message, "terminal", referenceId, "terminal", null);
        } catch (Exception e) {
            log.warn("Terminal notification failed: {}", e.getMessage());
        }
    }

    /**
     * Heartbeat: called on authenticated terminal pulls. Throttled in SQL
     * (at most once per 60s per terminal) to avoid write amplification.
     * Never throws — a failed heartbeat must not fail the pull.
     *
     * Runs in its own writable transaction (REQUIRES_NEW): callers like
     * MasterSyncController.pullMasterSync execute inside readOnly transactions,
     * and an UPDATE attempted there aborts/poisons the shared transaction so every
     * later query in the same request fails ("current transaction is aborted").
     */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void touchLastSeen(Long restaurantId, Long terminalId) {
        try {
            long now = System.currentTimeMillis();
            terminalRepository.touchLastSeen(terminalId, restaurantId, now, now - 60_000L);
        } catch (Exception e) {
            log.debug("Terminal heartbeat skipped: {}", e.getMessage());
        }
    }
}
