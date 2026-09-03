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
    private final StaffPermissionRevisionRepository revisionRepo;

    public PermissionService(StaffPermissionRepository permissionRepo,
                             PermissionRequestRepository requestRepo,
                             RoleTemplateRepository templateRepo,
                             UserRepository userRepo,
                             ObjectMapper objectMapper,
                             PushNotificationService pushNotificationService,
                             @org.springframework.beans.factory.annotation.Qualifier("permissionRequestRateLimiterDb")
                             DbRateLimiter permissionRequestRateLimiter,
                             SecurityAuditService securityAuditService,
                             StaffPermissionRevisionRepository revisionRepo) {
        this.permissionRepo = permissionRepo;
        this.requestRepo = requestRepo;
        this.templateRepo = templateRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
        this.pushNotificationService = pushNotificationService;
        this.permissionRequestRateLimiter = permissionRequestRateLimiter;
        this.securityAuditService = securityAuditService;
        this.revisionRepo = revisionRepo;
    }

    /**
     * Current monotonic authorization revision for a user (1 if none recorded yet).
     */
    public long getPermissionRevision(Long restaurantId, Long userId) {
        return revisionRepo.findByRestaurantIdAndUserId(restaurantId, userId)
                .map(StaffPermissionRevision::getRevision)
                .orElse(0L);
    }

    /**
     * Atomically increments and returns the user's authorization revision.
     * Called whenever a grant/revoke/template change alters offline authorization.
     */
    private long bumpRevision(Long restaurantId, Long userId) {
        var existing = revisionRepo.findByRestaurantIdAndUserId(restaurantId, userId);
        StaffPermissionRevision rev;
        if (existing.isPresent()) {
            rev = existing.get();
            rev.setRevision(rev.getRevision() + 1);
        } else {
            // First authorization-changing event for this user starts the counter at 1.
            rev = new StaffPermissionRevision(restaurantId, userId, 1L);
        }
        rev.setUpdatedAt(System.currentTimeMillis());
        revisionRepo.save(rev);
        return rev.getRevision();
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

    /**
     * The read-only baseline granted to a newly-created non-owner staff member:
     * pure "view" permissions only, no edit/settle/refund/menu-mutation authority.
     * A staffer starts able to see the menu and orders and today's summary, and must
     * request anything beyond that (P2 — default read-only).
     */
    public static final List<String> DEFAULT_READONLY_KEYS = List.of(
            PermissionKey.MENU_VIEW.getKey(),
            PermissionKey.ORDERS_VIEW.getKey(),
            PermissionKey.ORDERS_KOT_VIEW.getKey(),
            PermissionKey.REPORTS_DAY_SUMMARY.getKey()
    );

    /**
     * Apply the read-only baseline to a user. Idempotent (grantPermission upserts).
     * Owners/admins are all-powerful by role, so this is a no-op for them.
     */
    @Transactional
    public void grantDefaultReadOnly(Long restaurantId, Long userId, Long grantedBy) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null || isEffectiveOwner(user)) return;
        for (String key : DEFAULT_READONLY_KEYS) {
            grantPermission(restaurantId, userId, key, grantedBy);
        }
    }

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
        if (!wasGranted) {
            bumpRevision(restaurantId, userId); // grant changes offline authorization
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
                    if (wasGranted) {
                        long newRev = bumpRevision(restaurantId, userId);
                        // Stamp the revocation revision so sync revalidation can reject
                        // operations created at-or-after this point (Decision A strict),
                        // even if the permission is later re-granted.
                        perm.setLastRevokedRevision(newRev);
                    }
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
