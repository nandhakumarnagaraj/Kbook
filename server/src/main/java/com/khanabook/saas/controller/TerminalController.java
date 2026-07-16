package com.khanabook.saas.controller;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SecurityAuditService;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sync/terminal")
@RequiredArgsConstructor
public class TerminalController {

	private final RestaurantTerminalRepository terminalRepository;
	private final BillRepository billRepository;
	private final JwtUtility jwtUtility;
	private final SecurityAuditService securityAuditService;

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
	 * Reclaim an existing terminal after reinstall. The user picks their terminal from
	 * the list and this endpoint reassigns the terminal's deviceId to the new installation.
	 * Issues a fresh terminal token.
	 */
	@PostMapping("/reclaim")
	@Transactional
	public ResponseEntity<TerminalActivationResponse> reclaim(@RequestBody TerminalReclaimRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null || request == null
				|| request.terminalSeries() == null || request.terminalSeries().isBlank()
				|| request.deviceId() == null || request.deviceId().isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		String series = normalizeSeries(request.terminalSeries());
		RestaurantTerminal terminal = terminalRepository.findByRestaurantIdAndTerminalSeries(restaurantId, series)
				.orElse(null);
		if (terminal == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		if (Boolean.FALSE.equals(terminal.getIsActive())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Terminal is disabled");
		}
		// Reassign deviceId to this new installation
		terminal.setDeviceId(request.deviceId().trim());
		terminal.setUpdatedAt(System.currentTimeMillis());
		RestaurantTerminal saved = terminalRepository.save(terminal);

		securityAuditService.record("TERMINAL_RECLAIM", "SUCCESS",
				saved.getTerminalSeries(), request.deviceId());

		return ResponseEntity.ok(toResponse(saved));
	}

	public record TerminalListItem(String terminalId, String terminalName, String terminalSeries,
			Boolean isActive, Long lastActiveAt) {
	}

	public record TerminalReclaimRequest(String terminalSeries, String deviceId) {
	}

	@PostMapping("/activate")
	@Transactional
	public ResponseEntity<TerminalActivationResponse> activate(@RequestBody TerminalActivationRequest request) {
		Long restaurantId = TenantContext.getCurrentTenant();
		if (restaurantId == null || request == null || request.deviceId() == null
				|| request.deviceId().isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		String deviceId = request.deviceId().trim();
		RestaurantTerminal existing = terminalRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
				.orElse(null);
		if (existing != null) {
			existing.setUpdatedAt(System.currentTimeMillis());
			terminalRepository.save(existing);
			boolean active = existing.getIsActive() == null || existing.getIsActive();
			if (!active) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}
			return ResponseEntity.ok(toResponse(existing));
		}

		// Single-device reinstall recovery: if the restaurant has exactly one terminal,
		// reassign it to the new deviceId. A fresh install generates a new device UUID, so
		// the activation lookup by deviceId misses. This handles the common case where a
		// small restaurant uninstalls/reinstalls the app on the same tablet — it should keep
		// its existing terminal series (A) and bill history instead of getting a new one (B).
		// No staleness guard: an actively-used device would otherwise be forced onto a new
		// terminal and lose access to its own bills.
		List<RestaurantTerminal> allTerminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
		if (allTerminals.size() == 1) {
			RestaurantTerminal sole = allTerminals.get(0);
			if (sole.getIsActive() == null || sole.getIsActive()) {
				sole.setDeviceId(deviceId);
				sole.setUpdatedAt(System.currentTimeMillis());
				RestaurantTerminal saved = terminalRepository.save(sole);
				return ResponseEntity.ok(toResponse(saved));
			}
		}

		Set<String> assignedSeries = new HashSet<>();
		for (RestaurantTerminal terminal : allTerminals) {
			if (terminal.getTerminalSeries() != null && !terminal.getTerminalSeries().isBlank()) {
				assignedSeries.add(normalizeSeries(terminal.getTerminalSeries()));
			}
		}

		int seriesNumber = 1;
		while (assignedSeries.contains(seriesForNumber(seriesNumber))) {
			seriesNumber++;
		}
		String terminalSeries = seriesForNumber(seriesNumber);

		long now = System.currentTimeMillis();
		RestaurantTerminal terminal = new RestaurantTerminal();
		terminal.setRestaurantId(restaurantId);
		terminal.setTerminalSeries(terminalSeries);
		terminal.setTerminalName("Terminal " + terminalSeries);
		terminal.setDeviceId(deviceId);
		terminal.setIsActive(true);
		terminal.setCreatedAt(now);
		terminal.setUpdatedAt(now);
		RestaurantTerminal saved = terminalRepository.save(terminal);

		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
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

	public record TerminalActivationRequest(String deviceId) {
	}

	public record TerminalActivationResponse(String terminalId, String terminalName, String terminalSeries,
			Boolean isActive, Long registeredAt, Long lastVerifiedAt, String terminalToken) {
	}

	public record TerminalTransferRequest(String billPublicToken, String targetTerminalSeries) {
	}

	public record TerminalTransferResponse(String billPublicToken, String targetTerminalSeries) {
	}

	private TerminalActivationResponse toResponse(RestaurantTerminal terminal) {
		String terminalId = terminal.getId() != null ? terminal.getId().toString() : terminal.getTerminalSeries();
		String role = TenantContext.getCurrentRole() == null ? "OWNER" : TenantContext.getCurrentRole();
		String token = jwtUtility.generateTerminalToken(terminal.getTerminalSeries(), terminal.getRestaurantId(),
				role, terminalId, terminal.getTerminalSeries(), terminal.getDeviceId());
		return new TerminalActivationResponse(terminalId, terminal.getTerminalName(), terminal.getTerminalSeries(),
				terminal.getIsActive() == null ? Boolean.TRUE : terminal.getIsActive(), terminal.getCreatedAt(),
				terminal.getUpdatedAt(), token);
	}

	private String seriesForNumber(int number) {
		if (number >= 1 && number <= 26) {
			return String.valueOf((char) ('A' + number - 1));
		}
		return "T" + number;
	}

	private String normalizeSeries(String series) {
		if (series == null) {
			return "";
		}
		// Uppercase only. Do NOT fold a multi-character series such as the T-prefixed ones
		// generated for the 27th+ terminal (e.g. "T27") down to a single letter — that would
		// collapse every 27th+ terminal onto "T", make the activation dedup loop spin forever,
		// and break terminal-to-terminal transfer lookups.
		return series.trim().toUpperCase();
	}
}
