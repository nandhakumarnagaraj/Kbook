package com.khanabook.saas.service;

import com.khanabook.saas.dto.PermissionDtos.*;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
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

    public PermissionService(StaffPermissionRepository permissionRepo,
                             PermissionRequestRepository requestRepo,
                             RoleTemplateRepository templateRepo,
                             UserRepository userRepo) {
        this.permissionRepo = permissionRepo;
        this.requestRepo = requestRepo;
        this.templateRepo = templateRepo;
        this.userRepo = userRepo;
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    public boolean hasPermission(Long restaurantId, Long userId, String permissionKey) {
        var user = userRepo.findById(userId).orElse(null);
        if (user == null) return false;
        if (UserRole.OWNER == user.getRole()) return true;

        return permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey)
                .map(StaffPermission::getGranted)
                .orElse(false);
    }

    public List<String> getGrantedPermissions(Long restaurantId, Long userId) {
        var user = userRepo.findById(userId).orElse(null);
        if (user != null && UserRole.OWNER == user.getRole()) {
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
        var existing = permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey);
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
    }

    @Transactional
    public void revokePermission(Long restaurantId, Long userId, String permissionKey) {
        permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, permissionKey)
                .ifPresent(perm -> {
                    perm.setGranted(false);
                    perm.setRevokedAt(System.currentTimeMillis());
                    perm.setUpdatedAt(System.currentTimeMillis());
                    permissionRepo.save(perm);
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
        return requestRepo.save(request);
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
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    public List<RoleTemplate> getTemplates(Long restaurantId) {
        return templateRepo.findByRestaurantId(restaurantId);
    }

    @Transactional
    public RoleTemplate createTemplate(Long restaurantId, String name, String description,
                                       List<String> permissions, Long createdBy) {
        var json = "[" + permissions.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(",")) + "]";
        var template = new RoleTemplate(restaurantId, name, description, json, createdBy);
        return templateRepo.save(template);
    }

    // ── User permissions list ─────────────────────────────────────────────────

    public UserPermissionsResponse getUserPermissions(Long restaurantId, Long userId) {
        var user = userRepo.findById(userId).orElse(null);
        if (user == null) throw new IllegalArgumentException("User not found");

        var granted = getGrantedPermissions(restaurantId, userId);
        var permResponses = Arrays.stream(PermissionKey.values())
                .map(pk -> new PermissionResponse(
                        pk.getKey(),
                        pk.getDisplayName(),
                        pk.getCategory(),
                        granted.contains(pk.getKey()),
                        permissionRepo.findByRestaurantIdAndUserIdAndPermissionKey(restaurantId, userId, pk.getKey())
                                .map(StaffPermission::getGrantedAt).orElse(null)
                ))
                .collect(Collectors.toList());

        return new UserPermissionsResponse(userId, user.getName(), user.getRole().name(), permResponses);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> parsePermissionsList(String json) {
        return Arrays.stream(json.replaceAll("[\\[\\]\"]", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
