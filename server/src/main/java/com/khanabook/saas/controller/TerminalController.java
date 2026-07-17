package com.khanabook.saas.controller;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
import com.khanabook.saas.service.TerminalManagementService;
import com.khanabook.saas.utility.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sync/terminal")
@RequiredArgsConstructor
public class TerminalController {

	private final RestaurantTerminalRepository terminalRepository;
	private final BillRepository billRepository;
	private final DeviceRegistrationRequestRepository requestRepository;
	private final JwtUtility jwtUtility;
	private final SecurityAuditService securityAuditService;
	private final TerminalManagementService terminalManagementService;

	/**
	 * Returns all terminals for the authenticated restaurant.
	 * Used after reinstall to show a terminal picker so the user can reclaim their terminal.
	 */
	@org.springframework.web.bind.annotation.GetMapping("/list")
	public ResponseEntity<List<TerminalListItem>> listTerminals() {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null) return ResponseEntity.badRequest().build();
		List<RestaurantTerminal> terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
		List<TerminalListItem> items = terminals.stream()
				.map(t -> new TerminalListItem(
						t.getId() != null ? t.getId().toString() : t.getTerminalSeries(),
						t.getTerminalName(),
						t.getTerminalSeries(),
						t.getIsActive(),
						t.getUpdatedAt()))
				.collect(Collectors.toList());
		return ResponseEntity.ok(items);
	}

	/**
	 * Reclaim an existing terminal after reinstall. The OWNER or SHOP_ADMIN picks their
	 * terminal from the list and this endpoint reassigns the terminal's deviceId.
	 * Delegates to the central TerminalManagementService for atomic credential rotation.
	 *
	 * KBOOK_ADMIN is NOT allowed through this endpoint — use a separate support endpoint.
	 */
	@PostMapping("/reclaim")
	@Transactional
	public ResponseEntity<?> reclaim(@RequestBody TerminalReclaimRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		String role = TenantContext.getCurrentRole();
		if (restaurantId == null || request == null
				|| request.terminalSeries() == null || request.terminalSeries().isBlank()
				|| request.deviceId() == null || request.deviceId().isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		// KBOOK_ADMIN must not use the normal restaurant reclaim path
		if ("KBOOK_ADMIN".equals(role)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		String series = normalizeSeries(request.terminalSeries());
		String deviceId = request.deviceId().trim();

		RestaurantTerminal terminal = terminalRepository.findByRestaurantIdAndTerminalSeries(restaurantId, series)
				.orElse(null);
		if (terminal == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}

		// Validate terminal is eligible for reclaim
		if (!"ACTIVE".equals(terminal.getStatus())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new TerminalPendingResponse("DEACTIVATED", null, "Terminal is disabled"));
		}

		// Validate new deviceId is not already bound to another active terminal
		var conflicting = terminalRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId);
		if (conflicting.isPresent() && !conflicting.get().getId().equals(terminal.getId())) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new TerminalPendingResponse("DEVICE_ALREADY_BOUND", null,
							"This device is already bound to another terminal"));
		}

		// Delegate to central service for atomic recovery with credential rotation
		TerminalManagementService.ActivationResult result = terminalManagementService.recoverTerminal(
				terminal.getId(), restaurantId, deviceId, role != null ? role : "OWNER");

		return ResponseEntity.ok(toResponse(result.terminal()));
	}

	public record TerminalListItem(String terminalId, String terminalName, String terminalSeries,
			Boolean isActive, Long lastActiveAt) {
	}

	public record TerminalReclaimRequest(String terminalSeries, String deviceId) {
	}

	/**
	 * Polls the status of a device registration request. Returns status only —
	 * does NOT return terminal credentials. When APPROVED, the device must call
	 * POST /sync/terminal/complete-activation to securely obtain credentials.
	 */
	@org.springframework.web.bind.annotation.GetMapping("/request-status/{requestId}")
	public ResponseEntity<?> getRequestStatus(@org.springframework.web.bind.annotation.PathVariable Long requestId) {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null || requestId == null) return ResponseEntity.badRequest().build();

		var requestOpt = terminalManagementService.getRequestStatus(requestId, restaurantId);
		if (requestOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}

		var req = requestOpt.get();
		String status = req.getStatus();
		String message = switch (status) {
			case "PENDING" -> "Waiting for admin approval";
			case "APPROVED" -> "Approved — call complete-activation to obtain credentials";
			case "REJECTED" -> req.getRejectionReason() != null
					? "Rejected: " + req.getRejectionReason()
					: "Request was rejected by admin";
			case "EXPIRED" -> "Request has expired";
			default -> "Unknown status";
		};
		return ResponseEntity.ok(new TerminalPendingResponse(status, requestId, message));
	}

	/**
	 * Securely completes activation after a request is approved. Validates:
	 * - Request belongs to the authenticated restaurant
	 * - Request is APPROVED
	 * - Caller's deviceId matches the request's deviceId (proves same installation)
	 * Issues terminal credentials exactly once per approval.
	 */
	@PostMapping("/complete-activation")
	@Transactional
	public ResponseEntity<?> completeActivation(@RequestBody CompleteActivationRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null || request == null
				|| request.requestId() == null || request.deviceId() == null || request.deviceId().isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		String deviceId = request.deviceId().trim();

		var reqOpt = terminalManagementService.getRequestStatus(request.requestId(), restaurantId);
		if (reqOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}

		var req = reqOpt.get();

		// Must be APPROVED
		if (!"APPROVED".equals(req.getStatus())) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new TerminalPendingResponse(req.getStatus(), request.requestId(),
							"Request is not in APPROVED state"));
		}

		// Device binding: caller must present the same deviceId that was in the request
		if (!deviceId.equals(req.getDeviceId())) {
			securityAuditService.record("TERMINAL_COMPLETE_DEVICE_MISMATCH", "BLOCKED",
					null, deviceId + " != " + req.getDeviceId());
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new TerminalPendingResponse("DEVICE_MISMATCH", null,
							"Device does not match the approved request"));
		}

		// Retrieve the assigned terminal
		if (req.getAssignedTerminalId() == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new TerminalPendingResponse("NO_TERMINAL", null, "No terminal assigned"));
		}

		RestaurantTerminal terminal = terminalRepository.findById(req.getAssignedTerminalId())
				.filter(t -> t.getRestaurantId().equals(restaurantId))
				.orElse(null);
		if (terminal == null || !"ACTIVE".equals(terminal.getStatus())) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new TerminalPendingResponse("TERMINAL_UNAVAILABLE", null,
							"Assigned terminal is no longer active"));
		}

		// Idempotent: return the same terminal credentials
		// The terminal's deviceId should already be set from approval, but verify
		if (!deviceId.equals(terminal.getDeviceId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new TerminalPendingResponse("DEVICE_MISMATCH", null,
							"Terminal is bound to a different device"));
		}

		securityAuditService.record("TERMINAL_ACTIVATION_COMPLETED", "SUCCESS",
				terminal.getTerminalSeries(), deviceId);

		return ResponseEntity.ok(toResponse(terminal));
	}

	public record CompleteActivationRequest(Long requestId, String deviceId) {
	}

	@PostMapping("/activate")
	@Transactional
	public ResponseEntity<?> activate(@RequestBody TerminalActivationRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null || request == null || request.deviceId() == null
				|| request.deviceId().isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		String deviceId = request.deviceId().trim();
		String deviceModel = request.deviceModel() != null ? request.deviceModel().trim() : null;
		String role = TenantContext.getCurrentRole();

		// ── Case 1: Known device — terminal bound to this deviceId ──
		// A deviceId match proves identification, NOT authentication.
		// To receive terminal credentials, the caller must ALSO present a valid
		// terminal token for that same terminal (proving possession).
		// Without a valid terminal token, the deviceId match only identifies
		// which terminal to create a recovery request for.
		RestaurantTerminal existing = terminalRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
				.orElse(null);
		if (existing != null) {
			String callerTerminalDevice = TenantContext.getCurrentTerminalDevice();

			// Case 1a: Caller has a terminal token for a DIFFERENT device — impersonation attempt
			if (callerTerminalDevice != null && !callerTerminalDevice.isBlank()
					&& !callerTerminalDevice.equals(deviceId)) {
				securityAuditService.record("TERMINAL_IMPERSONATION_ATTEMPT", "BLOCKED",
						existing.getTerminalSeries(), callerTerminalDevice + " tried " + deviceId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
						.body(new TerminalPendingResponse("DEVICE_MISMATCH", null,
								"Terminal token device does not match requested device"));
			}

			// Case 1b: Caller has a valid terminal token matching this device — authenticated
			if (callerTerminalDevice != null && callerTerminalDevice.equals(deviceId)) {
				existing.setUpdatedAt(System.currentTimeMillis());
				terminalRepository.save(existing);
				if (!"ACTIVE".equals(existing.getStatus())) {
					return ResponseEntity.status(HttpStatus.FORBIDDEN)
							.body(new TerminalPendingResponse("DEACTIVATED", null,
									"Terminal has been deactivated by admin"));
				}
				return ResponseEntity.ok(toResponse(existing));
			}

			// Case 1c: No terminal token presented — deviceId matches but caller cannot
			// prove they are the original installation. Treat as recovery candidate.
			// This covers: reinstall (token lost), or another user who learned the deviceId.
			//
			// First check if admin has already approved a request for this device.
			// Without this check, every app restart creates a new PENDING request and
			// the device stays stuck even after approval.
			var approvedRequest = requestRepository.findByRestaurantIdAndDeviceIdAndStatus(
					restaurantId, deviceId, "APPROVED");
			if (approvedRequest.isPresent()) {
				var approved = approvedRequest.get();
				return ResponseEntity.ok()
						.body(new TerminalPendingResponse("APPROVED", approved.getId(),
								"Approved — call complete-activation to obtain credentials"));
			}

			Long userId = TenantContext.getCurrentUserId();
			DeviceRegistrationRequest recoveryRequest = terminalManagementService.createOrReuseRegistrationRequest(
					restaurantId,
					userId != null ? userId : 0L,
					deviceId,
					deviceModel,
					"RECOVERY",
					existing.getId());

			if (recoveryRequest == null) {
				return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
						.header("Retry-After", "300")
						.body(new TerminalPendingResponse("REJECTED_COOLDOWN", null,
								"Device was recently rejected. Please wait before requesting again."));
			}

			securityAuditService.record("TERMINAL_RECOVERY_REQUEST", "CREATED",
					existing.getTerminalSeries(), deviceId);

			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new TerminalPendingResponse("PENDING_APPROVAL", recoveryRequest.getId(),
							"Terminal recovery requires admin approval"));
		}

		// ── Case 4: First-ever terminal for a new restaurant ──
		// Only OWNER may automatically initialize Terminal A for a restaurant
		// that has NEVER had any terminal (no current or historical terminals).
		// Uses the restaurant-level lock to prevent concurrent first-device races.
		List<RestaurantTerminal> allTerminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
		if (allTerminals.isEmpty() && "OWNER".equals(role)) {
			// Atomic: lock restaurant to prevent concurrent first-device creation
			var profileOpt = terminalManagementService.lockRestaurantForTerminalOp(restaurantId);
			if (profileOpt.isEmpty()) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new TerminalPendingResponse("RESTAURANT_NOT_FOUND", null, "Restaurant not found"));
			}
			// Re-check after lock — another thread may have just created Terminal A
			List<RestaurantTerminal> afterLock = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
			if (!afterLock.isEmpty()) {
				// Race lost — fall through to PENDING path
			} else {
				long now = System.currentTimeMillis();
				RestaurantTerminal terminal = new RestaurantTerminal();
				terminal.setRestaurantId(restaurantId);
				terminal.setTerminalSeries("A");
				terminal.setTerminalName("Terminal A");
				terminal.setDeviceId(deviceId);
				terminal.setIsActive(true);
				terminal.setStatus("ACTIVE");
				terminal.setCredentialVersion(1L);
				terminal.setCreatedAt(now);
				terminal.setUpdatedAt(now);
				RestaurantTerminal saved = terminalRepository.save(terminal);
				securityAuditService.record("TERMINAL_FIRST_CREATED", "SUCCESS", "A", deviceId);
				return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
			}
		}

		// ── Case 2: Unknown physical device — always requires approval ──
		// This applies regardless of how many terminals the restaurant has.
		// A reinstall is NOT inferred from "only one terminal exists."
		// Recovery must go through explicit OWNER/SHOP_ADMIN approval.
		Long userId = TenantContext.getCurrentUserId();
		DeviceRegistrationRequest pending = terminalManagementService.createOrReuseRegistrationRequest(
				restaurantId,
				userId != null ? userId : 0L,
				deviceId,
				deviceModel,
				"NEW_DEVICE",
				null);

		if (pending == null) {
			// Cooldown in effect — recently rejected, cannot create new request yet
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.header("Retry-After", "300") // 5 minute cooldown
					.body(new TerminalPendingResponse("REJECTED_COOLDOWN", null,
							"Device was recently rejected. Please wait before requesting again."));
		}

		securityAuditService.record("TERMINAL_PENDING_REQUEST", "CREATED", null, deviceId);

		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new TerminalPendingResponse("PENDING_APPROVAL", pending.getId(),
						"Device registration is pending admin approval"));
	}

	/**
	 * Explicit, audited ownership transfer of a bill from the caller's terminal to
	 * another registered terminal. This is the only supported way to change a bill's
	 * owning terminal; a plain sync push that tries to change ownership is rejected.
	 */
	@PostMapping("/transfer")
	@Transactional
	public ResponseEntity<TerminalTransferResponse> transfer(@RequestBody TerminalTransferRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		String callerTerminalId = TenantContext.getCurrentTerminalId();
		if (restaurantId == null || callerTerminalId == null || request == null
				|| request.billPublicToken() == null || request.billPublicToken().isBlank()
				|| request.targetTerminalSeries() == null || request.targetTerminalSeries().isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		java.util.UUID publicToken;
		try {
			publicToken = java.util.UUID.fromString(request.billPublicToken());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		Optional<Bill> billOpt = billRepository.findByRestaurantIdAndPublicTokenAndIsDeletedFalse(restaurantId,
				publicToken);
		if (billOpt.isEmpty()) {
			securityAuditService.record("TERMINAL_TRANSFER", "BILL_NOT_FOUND", request.billPublicToken(),
					callerTerminalId);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		Bill bill = billOpt.get();
		String owner = bill.getCurrentOwnerTerminalId() != null ? bill.getCurrentOwnerTerminalId()
				: bill.getCreatedTerminalId();
		if (owner != null && !owner.equals(callerTerminalId)) {
			securityAuditService.record("TERMINAL_TRANSFER", "FORBIDDEN_NOT_OWNER", request.billPublicToken(),
					owner);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Bill is owned by a different terminal");
		}

		RestaurantTerminal target = terminalRepository
				.findByRestaurantIdAndTerminalSeries(restaurantId, normalizeSeries(request.targetTerminalSeries()))
				.orElse(null);
		if (target == null || Boolean.FALSE.equals(target.getIsActive())) {
			securityAuditService.record("TERMINAL_TRANSFER", "TARGET_INVALID", request.billPublicToken(),
					callerTerminalId);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		String targetTerminalId = target.getId() != null ? target.getId().toString() : target.getTerminalSeries();

		bill.setCurrentOwnerTerminalId(targetTerminalId);
		bill.setTerminalId(targetTerminalId);
		bill.setTerminalSeries(target.getTerminalSeries());
		billRepository.save(bill);
		securityAuditService.record("TERMINAL_TRANSFER", "SUCCESS", request.billPublicToken(), targetTerminalId);
		return ResponseEntity.ok(new TerminalTransferResponse(request.billPublicToken(), target.getTerminalSeries()));
	}

	public record TerminalActivationRequest(String deviceId, String deviceModel) {
	}

	public record TerminalActivationResponse(String terminalId, String terminalName, String terminalSeries,
			Boolean isActive, Long registeredAt, Long lastVerifiedAt, String terminalToken) {
	}

	public record TerminalPendingResponse(String status, Long requestId, String message) {
	}

	public record TerminalTransferRequest(String billPublicToken, String targetTerminalSeries) {
	}

	public record TerminalTransferResponse(String billPublicToken, String targetTerminalSeries) {
	}

	private TerminalActivationResponse toResponse(RestaurantTerminal terminal) {
		String terminalId = terminal.getId() != null ? terminal.getId().toString() : terminal.getTerminalSeries();
		String role = TenantContext.getCurrentRole() == null ? "OWNER" : TenantContext.getCurrentRole();
		String token = jwtUtility.generateTerminalToken(terminal.getTerminalSeries(), terminal.getRestaurantId(),
				role, terminalId, terminal.getTerminalSeries(), terminal.getDeviceId(),
				terminal.getCredentialVersion());
		return new TerminalActivationResponse(terminalId, terminal.getTerminalName(), terminal.getTerminalSeries(),
				terminal.getIsActive() == null ? Boolean.TRUE : terminal.getIsActive(), terminal.getCreatedAt(),
				terminal.getUpdatedAt(), token);
	}

	private String normalizeSeries(String series) {
		if (series == null) {
			return "";
		}
		return series.trim().toUpperCase();
	}
}
