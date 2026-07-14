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
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/sync/terminal")
@RequiredArgsConstructor
public class TerminalController {

	private final RestaurantTerminalRepository terminalRepository;
	private final BillRepository billRepository;
	private final JwtUtility jwtUtility;
	private final SecurityAuditService securityAuditService;

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

		Set<String> assignedSeries = new HashSet<>();
		for (RestaurantTerminal terminal : terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId)) {
			if (terminal.getTerminalSeries() != null) {
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
		String trimmed = series == null ? "" : series.trim();
		if (trimmed.isEmpty()) {
			return trimmed;
		}
		char first = Character.toUpperCase(trimmed.charAt(0));
		if (first >= 'A' && first <= 'Z') {
			return String.valueOf(first);
		}
		return trimmed.toUpperCase();
	}
}
