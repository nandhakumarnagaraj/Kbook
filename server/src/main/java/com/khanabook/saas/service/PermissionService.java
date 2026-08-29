package com.khanabook.saas.service;

import com.khanabook.saas.dto.PermissionDtos.*;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final StaffPermissionRepository permissionRepo;
    private final PermissionRequestRepository requestRepo;
    private final RoleTemplateRepository templateRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final PushNotificationService pushNotificationService;
    private final DbRateLimiter permissionRequestRateLimiter;
    private final SecurityAuditService securityAuditService;

    public PermissionService(StaffPermissionRepository permissionRepo,
                             PermissionRequestRepository requestRepo,
                             RoleTemplateRepository templateRepo,
                             UserRepository userRepo,
                             ObjectMapper objectMapper,
                             PushNotificationService pushNotificationService,
                             @org.springframework.beans.factory.annotation.Qualifier("permissionRequestRateLimiterDb")
                             DbRateLimiter permissionRequestRateLimiter,
                             SecurityAuditService securityAuditService) {
        this.permissionRepo = permissionRepo;
        this.requestRepo = requestRepo;
        this.templateRepo = templateRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
        this.pushNotificationService = pushNotificationService;
        this.permissionRequestRateLimiter = permissionRequestRateLimiter;
        this.securityAuditService = securityAuditService;
    }

    /**
     * Records a permission grant/revoke as a security audit event (actor from
     * TenantContext). Additive observability only — never throws into the caller.
     */
    private void auditPermissionChange(Long restaurantId, Long userId, String permissionKey,
                                       boolean wasGranted, boolean nowGranted) {
        if (wasGranted == nowGranted) return; // no state change
        try {
            String action = "PERMISSION_" + (nowGranted ? "GRANT" : "REVOKE");
            String outcome = (wasGranted ? "granted" : "revoked") + "->" + (nowGranted ? "granted" : "revoked");
            securityAuditService.record(action, outcome,
                    "user:" + userId + ":" + permissionKey, null);
        } catch (Exception e) {
            // audit must never break the permission flow
        }
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    public boolean hasPermission(Long restaurantId, Long userId, String permissionKey) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) return false;
        if (UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole()) return true;

        return permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey)
                .map(StaffPermission::getGranted)
                .orElse(false);
    }

    public List<String> getGrantedPermissions(Long restaurantId, Long userId) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) return List.of();
        if (UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole()) {
            return Arrays.stream(PermissionKey.values())
                    .map(PermissionKey::getKey)
                    .collect(Collectors.toList());
        }

        return permissionRepo.findByRestaurantIdAndUserIdAndGrantedTrue(restaurantId, userId)
                .stream()
                .map(StaffPermission::getPermissionKey)
                .collect(Collectors.toList());
    }

    public SyncPermissionsResponse getSyncPermissions(Long restaurantId, Long userId) {
        var granted = getGrantedPermissions(restaurantId, userId);
        var latestUpdate = permissionRepo.findByRestaurantIdAndUserId(restaurantId, userId)
                .stream()
                .mapToLong(StaffPermission::getUpdatedAt)
                .max()
                .orElse(0L);
        return new SyncPermissionsResponse(userId, granted, latestUpdate);
    }

    // ── Grant / Revoke ────────────────────────────────────────────────────────

    @Transactional
    public void grantPermission(Long restaurantId, Long userId, String permissionKey, Long grantedBy) {
        if (com.khanabook.saas.entity.PermissionKey.fromKey(permissionKey) == null) {
            throw new IllegalArgumentException("Invalid permission key: " + permissionKey);
        }
        requireTenantUser(restaurantId, userId);
        var existing = permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey);
        boolean wasGranted = existing.map(StaffPermission::getGranted).orElse(false);
        if (existing.isPresent()) {
            var perm = existing.get();
            perm.setGranted(true);
            perm.setGrantedBy(grantedBy);
            perm.setGrantedAt(System.currentTimeMillis());
            perm.setRevokedAt(null);
            perm.setUpdatedAt(System.currentTimeMillis());
            permissionRepo.save(perm);
        } else {
            permissionRepo.save(new StaffPermission(restaurantId, userId, permissionKey, grantedBy));
        }
        auditPermissionChange(restaurantId, userId, permissionKey, wasGranted, true);
    }

    @Transactional
    public void revokePermission(Long restaurantId, Long userId, String permissionKey) {
        requireTenantUser(restaurantId, userId);
        permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey)
                .ifPresent(perm -> {
                    boolean wasGranted = Boolean.TRUE.equals(perm.getGranted());
                    perm.setGranted(false);
                    perm.setRevokedAt(System.currentTimeMillis());
                    perm.setUpdatedAt(System.currentTimeMillis());
                    permissionRepo.save(perm);
                    auditPermissionChange(restaurantId, userId, permissionKey, wasGranted, false);
                });
    }

    @Transactional
    public void bulkGrant(Long restaurantId, Long userId, List<String> permissionKeys, Long grantedBy) {
        for (String key : permissionKeys) {
            grantPermission(restaurantId, userId, key, grantedBy);
        }
    }

    @Transactional
    public void applyTemplate(Long restaurantId, Long userId, Long templateId, Long grantedBy) {
        requireTenantUser(restaurantId, userId);
        var template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        if (!template.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Template not found");
        }

        var keys = parsePermissionsList(template.getPermissions());

        // Revoke all existing, then grant template permissions
        permissionRepo.findByRestaurantIdAndUserId(restaurantId, userId)
                .forEach(p -> {
                    p.setGranted(false);
                    p.setRevokedAt(System.currentTimeMillis());
                    p.setUpdatedAt(System.currentTimeMillis());
                    permissionRepo.save(p);
                });

        bulkGrant(restaurantId, userId, keys, grantedBy);
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    @Transactional
    public PermissionRequest submitRequest(Long restaurantId, Long userId, String permissionKey, String reason) {
        if (!permissionRequestRateLimiter.tryConsume("r" + restaurantId + ":u" + userId)) {
            throw new IllegalStateException("Too many permission requests. Try again later.");
        }
        // Check if already has permission
        if (hasPermission(restaurantId, userId, permissionKey)) {
            throw new IllegalStateException("You already have this permission");
        }
        // Check for existing pending request
        var existing = requestRepo.findByRestaurantIdAndUserIdAndPermissionKeyAndStatus(
                restaurantId, userId, permissionKey, "PENDING");
        if (!existing.isEmpty()) {
            throw new IllegalStateException("A request for this permission is already pending");
        }

        var request = new PermissionRequest(restaurantId, userId, permissionKey, reason);
        var saved = requestRepo.save(request);

        notifyOwnersOfRequest(saved);
        return saved;
    }

    public List<PermissionRequest> getPendingRequests(Long restaurantId) {
        return requestRepo.findByRestaurantIdAndStatus(restaurantId, "PENDING");
    }

    @Transactional
    public void approveRequest(Long requestId, Long resolvedBy) {
        var request = requestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        Long callerRestaurant = com.khanabook.saas.security.TenantContext.getCurrentTenant();
        if (callerRestaurant != null && !callerRestaurant.equals(request.getRestaurantId())) {
            throw new IllegalArgumentException("Request not found");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is no longer pending");
        }

        request.setStatus("APPROVED");
        request.setResolvedBy(resolvedBy);
        request.setResolvedAt(System.currentTimeMillis());
        requestRepo.save(request);

        // Auto-grant the permission
        grantPermission(request.getRestaurantId(), request.getUserId(), request.getPermissionKey(), resolvedBy);

        notifyRequester(request, "Permission Approved",
                displayName(request.getPermissionKey()) + " was approved for your account.",
                "permission_approved", null);
    }

    @Transactional
    public void rejectRequest(Long requestId, Long resolvedBy, String rejectionReason) {
        var request = requestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        Long callerRestaurant = com.khanabook.saas.security.TenantContext.getCurrentTenant();
        if (callerRestaurant != null && !callerRestaurant.equals(request.getRestaurantId())) {
            throw new IllegalArgumentException("Request not found");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is no longer pending");
        }

        request.setStatus("REJECTED");
        request.setResolvedBy(resolvedBy);
        request.setResolvedAt(System.currentTimeMillis());
        request.setRejectionReason(rejectionReason);
        requestRepo.save(request);

        notifyRequester(request, "Permission Rejected",
                displayName(request.getPermissionKey()) + " was rejected"
                        + (rejectionReason != null && !rejectionReason.isBlank() ? ": " + rejectionReason : "."),
                "permission_rejected", rejectionReason);
    }

    // ── Request notifications ────────────────────────────────────────────────

    private void notifyOwnersOfRequest(PermissionRequest request) {
        try {
            var owners = userRepo.findByRestaurantIdAndRoleAndIsDeletedFalse(
                    request.getRestaurantId(), UserRole.OWNER);
            List<Long> ownerIds = owners.stream().map(User::getId).toList();
            String requesterName = userRepo.findById(request.getUserId())
                    .map(User::getName).orElse("Staff");
            pushNotificationService.pushToUsers(
                    request.getRestaurantId(),
                    ownerIds,
                    "Permission Request",
                    requesterName + " requests " + displayName(request.getPermissionKey())
                            + (request.getReason() != null && !request.getReason().isBlank()
                                    ? " — " + request.getReason() : ""),
                    "permission_request",
                    String.valueOf(request.getId()),
                    "permission_request",
                    null);
        } catch (Exception e) {
            // Notification failure must never break the request flow
        }
    }

    private void notifyRequester(PermissionRequest request, String title, String message,
                                 String type, String rejectionReason) {
        try {
            pushNotificationService.pushToUsers(
                    request.getRestaurantId(),
                    List.of(request.getUserId()),
                    title,
                    message,
                    type,
                    String.valueOf(request.getId()),
                    "permission_request",
                    null);
        } catch (Exception e) {
            // Notification failure must never break the approval/rejection flow
        }
    }

    private String displayName(String permissionKey) {
        var pk = PermissionKey.fromKey(permissionKey);
        return pk != null ? pk.getDisplayName() : permissionKey;
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    public List<RoleTemplate> getTemplates(Long restaurantId) {
        return templateRepo.findByRestaurantId(restaurantId);
    }

    @Transactional
    public RoleTemplate createTemplate(Long restaurantId, String name, String description,
                                       List<String> permissions, Long createdBy) {
        for (String key : permissions) {
            if (PermissionKey.fromKey(key) == null) {
                throw new IllegalArgumentException("Invalid permission key: " + key);
            }
        }
        try {
            var json = objectMapper.writeValueAsString(permissions);
            var template = new RoleTemplate(restaurantId, name, description, json, createdBy);
            return templateRepo.save(template);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize permissions", e);
        }
    }

    // ── User permissions list ─────────────────────────────────────────────────

    public UserPermissionsResponse getUserPermissions(Long restaurantId, Long userId) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) throw new IllegalArgumentException("User not found");

        var grantedSet = permissionRepo.findByRestaurantIdAndUserIdAndGrantedTrue(restaurantId, userId).stream()
                .map(StaffPermission::getPermissionKey)
                .collect(Collectors.toSet());
        var grantedAtMap = permissionRepo.findByRestaurantIdAndUserId(restaurantId, userId).stream()
                .collect(Collectors.toMap(StaffPermission::getPermissionKey, StaffPermission::getGrantedAt, (a, b) -> a));

        var permResponses = Arrays.stream(PermissionKey.values())
                .map(pk -> new PermissionResponse(
                        pk.getKey(),
                        pk.getDisplayName(),
                        pk.getCategory(),
                        isEffectiveOwner(user) || grantedSet.contains(pk.getKey()),
                        grantedAtMap.get(pk.getKey())
                ))
                .collect(Collectors.toList());

        return new UserPermissionsResponse(userId, user.getName(), user.getRole().name(), permResponses);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isEffectiveOwner(User user) {
        return UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole();
    }

    private User findTenantUser(Long restaurantId, Long userId) {
        return userRepo.findById(userId)
                .filter(u -> u.getRestaurantId() != null && u.getRestaurantId().equals(restaurantId))
                .orElse(null);
    }

    private User requireTenantUser(Long restaurantId, Long userId) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) throw new IllegalArgumentException("User not found");
        return user;
    }

    private List<String> parsePermissionsList(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid permissions payload", e);
        }
    }
}
