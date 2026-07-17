package com.khanabook.saas.controller;

import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.TerminalManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Management endpoints for OWNER and SHOP_ADMIN roles to manage terminals
 * and device registration requests. Secured via SecurityConfig to
 * /business/terminals/** and /business/terminal-requests/**.
 */
@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class TerminalManagementController {

    private final TerminalManagementService terminalManagementService;
    private final RestaurantTerminalRepository terminalRepository;
    private final DeviceRegistrationRequestRepository requestRepository;

    // ── Terminal List & Details ──────────────────────────────────────────────────

    @GetMapping("/terminals")
    public ResponseEntity<List<TerminalDto>> listTerminals() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) return ResponseEntity.badRequest().build();

        List<RestaurantTerminal> terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        List<TerminalDto> dtos = terminals.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // ── Deactivate ──────────────────────────────────────────────────────────────

    @PostMapping("/terminals/{terminalId}/deactivate")
    public ResponseEntity<Void> deactivateTerminal(@PathVariable Long terminalId) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) return ResponseEntity.badRequest().build();

        terminalManagementService.deactivateTerminal(terminalId, restaurantId);
        return ResponseEntity.noContent().build();
    }

    // ── Rename ──────────────────────────────────────────────────────────────────

    @PostMapping("/terminals/{terminalId}/rename")
    public ResponseEntity<TerminalDto> renameTerminal(
            @PathVariable Long terminalId, @RequestBody RenameRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null || request == null
                || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        RestaurantTerminal terminal = terminalManagementService.renameTerminal(
                terminalId, restaurantId, request.name().trim());
        return ResponseEntity.ok(toDto(terminal));
    }

    // ── Device Registration Requests ────────────────────────────────────────────

    @GetMapping("/terminal-requests")
    public ResponseEntity<List<RequestDto>> listRequests(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) return ResponseEntity.badRequest().build();

        List<DeviceRegistrationRequest> requests;
        if ("ALL".equalsIgnoreCase(status)) {
            requests = requestRepository.findByRestaurantIdOrderByRequestedAtDesc(restaurantId);
        } else {
            requests = requestRepository.findByRestaurantIdAndStatusOrderByRequestedAtDesc(restaurantId, status.toUpperCase());
        }
        List<RequestDto> dtos = requests.stream().map(this::toRequestDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/terminal-requests/{requestId}/approve")
    public ResponseEntity<ApprovalResponse> approveRequest(@PathVariable Long requestId) {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        String role = TenantContext.getCurrentRole();
        if (restaurantId == null) return ResponseEntity.badRequest().build();

        TerminalManagementService.ActivationResult result = terminalManagementService.approveRequest(
                requestId, restaurantId, userId != null ? userId : 0L, role != null ? role : "OWNER");

        return ResponseEntity.ok(new ApprovalResponse(
                result.terminal().getId(),
                result.terminal().getTerminalSeries(),
                result.terminal().getTerminalName(),
                result.terminal().getStatus()));
    }

    @PostMapping("/terminal-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable Long requestId, @RequestBody(required = false) RejectRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        if (restaurantId == null) return ResponseEntity.badRequest().build();

        String reason = (request != null && request.reason() != null) ? request.reason().trim() : null;
        terminalManagementService.rejectRequest(requestId, restaurantId,
                userId != null ? userId : 0L, reason);
        return ResponseEntity.noContent().build();
    }

    // ── Recovery (rebind terminal to new device) ────────────────────────────────

    @PostMapping("/terminals/{terminalId}/recover")
    public ResponseEntity<RecoveryResponse> recoverTerminal(
            @PathVariable Long terminalId, @RequestBody RecoverRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        String role = TenantContext.getCurrentRole();
        if (restaurantId == null || request == null
                || request.deviceId() == null || request.deviceId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        TerminalManagementService.ActivationResult result = terminalManagementService.recoverTerminal(
                terminalId, restaurantId, request.deviceId().trim(), role != null ? role : "OWNER");

        return ResponseEntity.ok(new RecoveryResponse(
                result.terminal().getId(),
                result.terminal().getTerminalSeries(),
                result.terminal().getTerminalName(),
                result.terminalToken()));
    }

    // ── DTOs ────────────────────────────────────────────────────────────────────

    public record TerminalDto(Long id, String terminalSeries, String terminalName,
                              String status, Boolean isActive, String deviceId,
                              Long credentialVersion, Long createdAt, Long updatedAt) {
    }

    public record RequestDto(Long id, String deviceId, String deviceModel, String deviceName,
                             String requestType, String status, Long matchedTerminalId,
                             Long requestedAt, Long processedAt, String rejectionReason,
                             Long assignedTerminalId) {
    }

    public record RenameRequest(String name) {
    }

    public record RejectRequest(String reason) {
    }

    public record RecoverRequest(String deviceId) {
    }

    public record ApprovalResponse(Long terminalId, String terminalSeries, String terminalName, String status) {
    }

    public record RecoveryResponse(Long terminalId, String terminalSeries, String terminalName, String terminalToken) {
    }

    // ── Mappers ─────────────────────────────────────────────────────────────────

    private TerminalDto toDto(RestaurantTerminal t) {
        return new TerminalDto(t.getId(), t.getTerminalSeries(), t.getTerminalName(),
                t.getStatus(), t.getIsActive(), t.getDeviceId(),
                t.getCredentialVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private RequestDto toRequestDto(DeviceRegistrationRequest r) {
        return new RequestDto(r.getId(), r.getDeviceId(), r.getDeviceModel(), r.getDeviceName(),
                r.getRequestType(), r.getStatus(), r.getMatchedTerminalId(),
                r.getRequestedAt(), r.getProcessedAt(), r.getRejectionReason(),
                r.getAssignedTerminalId());
    }
}
