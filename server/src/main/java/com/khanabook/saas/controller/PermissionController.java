package com.khanabook.saas.controller;

import com.khanabook.saas.dto.PermissionDtos.*;
import com.khanabook.saas.entity.PermissionKey;
import com.khanabook.saas.entity.PermissionRequest;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;
    private final com.khanabook.saas.repository.UserRepository userRepo;

    public PermissionController(PermissionService permissionService,
                                com.khanabook.saas.repository.UserRepository userRepo) {
        this.permissionService = permissionService;
        this.userRepo = userRepo;
    }

    // ── Available permissions catalog ─────────────────────────────────────────

    @GetMapping("/catalog")
    public ResponseEntity<List<Map<String, String>>> getCatalog() {
        var catalog = Arrays.stream(PermissionKey.values())
                .map(pk -> Map.of(
                        "key", pk.getKey(),
                        "displayName", pk.getDisplayName(),
                        "category", pk.getCategory()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(catalog);
    }

    // ── User permissions (view) ───────────────────────────────────────────────

    @GetMapping("/users/{userId}")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<UserPermissionsResponse> getUserPermissions(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        var response = permissionService.getUserPermissions(currentUser.getRestaurantId(), userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SyncPermissionsResponse> getMyPermissions(@AuthenticationPrincipal User currentUser) {
        var response = permissionService.getSyncPermissions(currentUser.getRestaurantId(), currentUser.getId());
        return ResponseEntity.ok(response);
    }

    // ── Grant / Revoke (Owner only) ──────────────────────────────────────────

    @PostMapping("/grant")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<Void> grant(
            @Valid @RequestBody GrantPermissionRequest request,
            @AuthenticationPrincipal User currentUser) {
        permissionService.grantPermission(
                currentUser.getRestaurantId(),
                request.userId(),
                request.permissionKey(),
                currentUser.getId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<Void> revoke(
            @Valid @RequestBody RevokePermissionRequest request,
            @AuthenticationPrincipal User currentUser) {
        permissionService.revokePermission(
                currentUser.getRestaurantId(),
                request.userId(),
                request.permissionKey()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk-grant")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<Void> bulkGrant(
            @Valid @RequestBody BulkGrantRequest request,
            @AuthenticationPrincipal User currentUser) {
        permissionService.bulkGrant(
                currentUser.getRestaurantId(),
                request.userId(),
                request.permissionKeys(),
                currentUser.getId()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/apply-template")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<Void> applyTemplate(
            @Valid @RequestBody ApplyTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        permissionService.applyTemplate(
                currentUser.getRestaurantId(),
                request.userId(),
                request.templateId(),
                currentUser.getId()
        );
        return ResponseEntity.ok().build();
    }

    // ── Permission Requests (Staff submits, Owner resolves) ──────────────────

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> submitRequest(
            @Valid @RequestBody RequestPermissionRequest request,
            @AuthenticationPrincipal User currentUser) {
        var saved = permissionService.submitRequest(
                currentUser.getRestaurantId(),
                currentUser.getId(),
                request.permissionKey(),
                request.reason()
        );
        return ResponseEntity.ok(Map.of("requestId", saved.getId(), "status", saved.getStatus()));
    }

    @GetMapping("/requests/pending")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<List<PermissionRequestResponse>> getPendingRequests(
            @AuthenticationPrincipal User currentUser) {
        var requests = permissionService.getPendingRequests(currentUser.getRestaurantId());
        var responses = requests.stream().map(this::toRequestResponse).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/requests/{requestId}/resolve")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<Void> resolveRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ResolveRequestRequest request,
            @AuthenticationPrincipal User currentUser) {
        if ("APPROVE".equalsIgnoreCase(request.action())) {
            permissionService.approveRequest(requestId, currentUser.getId());
        } else if ("REJECT".equalsIgnoreCase(request.action())) {
            permissionService.rejectRequest(requestId, currentUser.getId(), request.rejectionReason());
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    // ── Role Templates ────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<List<RoleTemplateResponse>> getTemplates(@AuthenticationPrincipal User currentUser) {
        var templates = permissionService.getTemplates(currentUser.getRestaurantId());
        var responses = templates.stream().map(t -> new RoleTemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                parsePermissions(t.getPermissions()),
                t.getIsDefault(),
                t.getCreatedAt()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/templates")
    @RequireRole({UserRole.OWNER})
    public ResponseEntity<RoleTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        var template = permissionService.createTemplate(
                currentUser.getRestaurantId(),
                request.name(),
                request.description(),
                request.permissions(),
                currentUser.getId()
        );
        return ResponseEntity.ok(new RoleTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                request.permissions(),
                template.getIsDefault(),
                template.getCreatedAt()
        ));
    }

    // ── Check permission (used by other services/sync) ────────────────────────

    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @RequestParam String permissionKey,
            @AuthenticationPrincipal User currentUser) {
        boolean has = permissionService.hasPermission(
                currentUser.getRestaurantId(), currentUser.getId(), permissionKey);
        return ResponseEntity.ok(Map.of("granted", has));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PermissionRequestResponse toRequestResponse(PermissionRequest req) {
        var pk = PermissionKey.fromKey(req.getPermissionKey());
        var requester = userRepo.findById(req.getUserId()).orElse(null);
        return new PermissionRequestResponse(
                req.getId(),
                req.getUserId(),
                requester != null ? requester.getName() : null,
                req.getPermissionKey(),
                pk != null ? pk.getDisplayName() : req.getPermissionKey(),
                req.getStatus(),
                req.getReason(),
                req.getRequestedAt(),
                req.getResolvedAt(),
                req.getRejectionReason()
        );
    }

    private List<String> parsePermissions(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
