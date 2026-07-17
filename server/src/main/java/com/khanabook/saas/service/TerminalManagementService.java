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

    private final RestaurantTerminalRepository terminalRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final DeviceRegistrationRequestRepository requestRepository;
    private final JwtUtility jwtUtility;
    private final SecurityAuditService securityAuditService;

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
            return existing.get();
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
        return requestRepository.save(request);
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
            // Recovery: rebind existing terminal (no new slot consumed)
            terminal = terminalRepository.findById(request.getMatchedTerminalId())
                    .filter(t -> t.getRestaurantId().equals(restaurantId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matched terminal not found"));

            terminal.setDeviceId(request.getDeviceId());
            terminal.setStatus("ACTIVE");
            terminal.setIsActive(true);
            terminal.setCredentialVersion(terminal.getCredentialVersion() + 1); // revokes old tokens
            terminal.setUpdatedAt(System.currentTimeMillis());
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
        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> t.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Terminal not found"));

        if (!"ACTIVE".equals(terminal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_ALREADY_INACTIVE");
        }

        terminal.setStatus("INACTIVE");
        terminal.setIsActive(false);
        terminal.setCredentialVersion(terminal.getCredentialVersion() + 1); // revokes old tokens
        terminal.setUpdatedAt(System.currentTimeMillis());
        terminalRepository.save(terminal);

        securityAuditService.record("TERMINAL_DEACTIVATED", "SUCCESS",
                terminal.getTerminalSeries(), terminal.getDeviceId());
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

            securityAuditService.record("TERMINAL_RECOVERED", "SUCCESS",
                    terminal.getTerminalSeries(), newDeviceId);
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
}
