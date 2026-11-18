package com.khanabook.saas.feature.staff.service;

import com.khanabook.saas.feature.auth.data.PermissionDtos.*;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final UserRepository userRepo;

    public PermissionService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * Constant monotonic authorization revision (always 1L for static role permissions).
     */
    public long getPermissionRevision(Long restaurantId, Long userId) {
        return 1L;
    }

    public boolean hasPermission(Long restaurantId, Long userId, String permissionKey) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) return false;
        if (UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole()) return true;
        if (UserRole.SHOP_STAFF == user.getRole()) {
            return SHOP_STAFF_GRANTED_KEYS.contains(permissionKey);
        }
        return false;
    }

    public List<String> getGrantedPermissions(Long restaurantId, Long userId) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) return List.of();
        if (UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole()) {
            return Arrays.stream(PermissionKey.values())
                    .map(PermissionKey::getKey)
                    .collect(Collectors.toList());
        }
        if (UserRole.SHOP_STAFF == user.getRole()) {
            return new ArrayList<>(SHOP_STAFF_GRANTED_KEYS);
        }
        return List.of();
    }

    public SyncPermissionsResponse getSyncPermissions(Long restaurantId, Long userId) {
        var granted = getGrantedPermissions(restaurantId, userId);
        return new SyncPermissionsResponse(userId, granted, System.currentTimeMillis());
    }

    public static final List<String> DEFAULT_READONLY_KEYS = List.of(
            PermissionKey.MENU_VIEW.getKey(),
            PermissionKey.ORDERS_VIEW.getKey(),
            PermissionKey.REPORTS_DAY_SUMMARY.getKey()
    );

    public static final Set<String> SHOP_STAFF_CONFIG_KEYS = Set.of(
            // Settings (printer config is granted to staff for POS hardware pairing; all other settings are owner-only)
            PermissionKey.SETTINGS_SHOP_PROFILE.getKey(),
            PermissionKey.SETTINGS_PAYMENT.getKey(),
            PermissionKey.SETTINGS_TERMINAL.getKey(),
            PermissionKey.SETTINGS_GST.getKey(),
            // Staff management (strictly owner-only)
            PermissionKey.STAFF_VIEW.getKey(),
            PermissionKey.STAFF_ADD.getKey(),
            PermissionKey.STAFF_EDIT.getKey(),
            PermissionKey.STAFF_REMOVE.getKey(),
            PermissionKey.STAFF_PERMISSIONS.getKey(),
            // Reports (export and GST reports are owner-only)
            PermissionKey.REPORTS_FULL.getKey(),
            PermissionKey.REPORTS_GST.getKey(),
            PermissionKey.REPORTS_EXPORT.getKey(),
            // Menu editing (strictly owner-only; staff can only view menu items)
            PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey(),
            PermissionKey.MENU_EDIT_PRICE.getKey(),
            PermissionKey.MENU_EDIT_FULL.getKey(),
            PermissionKey.MENU_ADD_ITEM.getKey(),
            PermissionKey.MENU_DELETE_ITEM.getKey()
    );

    public static final Set<String> SHOP_STAFF_GRANTED_KEYS =
            Arrays.stream(PermissionKey.values())
                    .map(PermissionKey::getKey)
                    .filter(key -> !SHOP_STAFF_CONFIG_KEYS.contains(key))
                    .collect(Collectors.toUnmodifiableSet());

    public void applyReadOnlyBaseline(Long restaurantId, Long userId, Long grantedBy) {
        // No-op: SHOP_STAFF is already constant and role-bound.
    }

    public void grantDefaultReadOnly(Long restaurantId, Long userId, Long grantedBy) {
        // No-op: SHOP_STAFF is already constant and role-bound.
    }

    public void grantPermission(Long restaurantId, Long userId, String permissionKey, Long grantedBy) {
        log.info("grantPermission called for user {}: ignored (permissions are constant by role)", userId);
    }

    public void bulkGrant(Long restaurantId, Long userId, List<String> permissionKeys, Long grantedBy) {
        log.info("bulkGrant called for user {}: ignored (permissions are constant by role)", userId);
    }

    public void revokePermission(Long restaurantId, Long userId, String permissionKey) {
        log.info("revokePermission called for user {}: ignored (permissions are constant by role)", userId);
    }

    public void applyTemplate(Long restaurantId, Long userId, Long templateId, Long appliedBy) {
        log.info("applyTemplate called for user {}: ignored (role templates retired)", userId);
    }

    public PermissionRequest submitRequest(Long restaurantId, Long userId, String permissionKey, String reason) {
        return new PermissionRequest(restaurantId, userId, permissionKey, reason);
    }

    public List<PermissionRequest> getPendingRequests(Long restaurantId) {
        return List.of();
    }

    public void approveRequest(Long requestId, Long resolvedBy) {
        log.info("approveRequest called for requestId {}: ignored (permission requests retired)", requestId);
    }

    public void rejectRequest(Long requestId, Long resolvedBy, String rejectionReason) {
        log.info("rejectRequest called for requestId {}: ignored (permission requests retired)", requestId);
    }

    public void resolveRequest(Long restaurantId, Long requestId, boolean approve, String rejectionReason, Long resolvedBy) {
        // No-op
    }

    public List<RoleTemplate> getTemplates(Long restaurantId) {
        return List.of();
    }

    public RoleTemplate createTemplate(Long restaurantId, String name, String description,
                                       List<String> permissions, Long createdBy) {
        return new RoleTemplate(restaurantId, name, description, "[]", createdBy);
    }

    public UserPermissionsResponse getUserPermissions(Long restaurantId, Long userId) {
        var user = findTenantUser(restaurantId, userId);
        if (user == null) throw new IllegalArgumentException("User not found");

        var granted = getGrantedPermissions(restaurantId, userId);
        var grantedSet = Set.copyOf(granted);

        var permResponses = Arrays.stream(PermissionKey.values())
                .map(pk -> new PermissionResponse(
                        pk.getKey(),
                        pk.getDisplayName(),
                        pk.getCategory(),
                        isEffectiveOwner(user) || grantedSet.contains(pk.getKey()),
                        null
                ))
                .collect(Collectors.toList());

        return new UserPermissionsResponse(userId, user.getName(), user.getRole().name(), permResponses);
    }

    private boolean isEffectiveOwner(User user) {
        return UserRole.OWNER == user.getRole() || UserRole.KBOOK_ADMIN == user.getRole();
    }

    private User findTenantUser(Long restaurantId, Long userId) {
        return userRepo.findById(userId)
                .filter(u -> u.getRestaurantId() != null && u.getRestaurantId().equals(restaurantId))
                .orElse(null);
    }
}
