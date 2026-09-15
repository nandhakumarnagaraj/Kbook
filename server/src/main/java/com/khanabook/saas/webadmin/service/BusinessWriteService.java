package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.exception.DuplicateStaffPhoneException;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.webadmin.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class BusinessWriteService {

    private static final Logger log = LoggerFactory.getLogger(BusinessWriteService.class);
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantTerminalRepository terminalRepository;
    private final RestaurantProfileRepository profileRepository;
    private final PermissionService permissionService;
    private final com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService;

    public BusinessWriteService(UserRepository userRepository,
                                CategoryRepository categoryRepository,
                                MenuItemRepository menuItemRepository,
                                RestaurantTerminalRepository terminalRepository,
                                RestaurantProfileRepository profileRepository,
                                PermissionService permissionService,
                                com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.terminalRepository = terminalRepository;
        this.profileRepository = profileRepository;
        this.permissionService = permissionService;
        this.passwordResetOtpService = passwordResetOtpService;
    }

    // ─── Staff CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public StaffCreatedResponse createStaff(Long restaurantId, CreateStaffRequest req) {
        UserRole role = parseRole(req.role());

        // Only a LIVE (not soft-deleted) account blocks re-adding this phone.
        // Soft-deleted staff must not reserve the number forever — mirrors signup
        // (AuthServiceImpl#ensurePhoneNumberAvailableForSignup).
        if (userRepository.findActiveByAnyIdentifier(req.phone()).isPresent()) {
            throw new DuplicateStaffPhoneException();
        }
        // Release the identifier from any soft-deleted rows so the partial unique
        // indexes (phone_number / login_id / whatsapp_number) don't block reuse.
        releaseIdentifierFromDeletedUsers(req.phone());

        // OTP-based onboarding: no temp password is generated or shared. The account
        // is created with an unguessable hash so nobody can log in until the staff
        // member sets their own password via the OTP (Forgot Password) flow. This
        // removes the shared-secret weak link where the owner knew each staff password.
        String hash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

        User user = new User();
        user.setName(req.name());
        user.setPhoneNumber(req.phone());
        user.setLoginId(req.phone());
        user.setWhatsappNumber(req.phone());
        user.setEmail(req.email());
        user.setRole(role);
        user.setPasswordHash(hash);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setIsActive(true);
        user.setRestaurantId(restaurantId);
        user.setDeviceId("web-admin");
        user.setLocalId(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        user.setCreatedAt(now);
        touch(user, now);

        User saved = userRepository.save(user);
        log.info("Staff created: userId={}, restaurant={}, role={}", saved.getId(), restaurantId, role);

        List<String> customPermissions = req.permissions();
        if (customPermissions != null && !customPermissions.isEmpty()) {
            permissionService.bulkGrant(restaurantId, saved.getId(), customPermissions, TenantContext.getCurrentUserId());
        } else {
            permissionService.grantDefaultReadOnly(restaurantId, saved.getId(), TenantContext.getCurrentUserId());
        }

        // Auto-issue an OTP straight to the staff member's phone so they can set
        // their own password on first login. Never block staff creation if the
        // OTP send fails (owner can trigger a resend from the login screen).
        boolean otpSent;
        try {
            passwordResetOtpService.issueOtp(saved.getPhoneNumber());
            otpSent = true;
        } catch (RuntimeException e) {
            log.warn("Staff created but onboarding OTP send failed for userId={}: {}",
                    saved.getId(), e.getMessage());
            otpSent = false;
        }

        return new StaffCreatedResponse(
                saved.getId(), saved.getName(), saved.getPhoneNumber(),
                saved.getRole().name(), otpSent
        );
    }

    @Transactional
    public void updateStaff(Long restaurantId, Long userId, UpdateStaffRequest req) {
        User user = userRepository.findById(userId)
                .filter(u -> restaurantId.equals(u.getRestaurantId()) && !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));

        UserRole newRole = parseRole(req.role());
        boolean roleChanged = user.getRole() != newRole;
        boolean currentUser = userId.equals(TenantContext.getCurrentUserId());
        if (currentUser && roleChanged) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        if ((!req.phone().equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(req.phone()))
                || (!req.phone().equals(user.getLoginId()) && userRepository.existsByLoginId(req.phone()))) {
            throw new DuplicateStaffPhoneException();
        }

        user.setName(req.name());
        user.setPhoneNumber(req.phone());
        user.setLoginId(req.phone());
        user.setWhatsappNumber(req.phone());
        user.setEmail(req.email());
        user.setRole(newRole);
        touch(user, System.currentTimeMillis());

        if (roleChanged) {
            user.setTokenInvalidatedAt(System.currentTimeMillis());
        }

        userRepository.save(user);
        log.info("Staff updated: userId={}, roleChanged={}", userId, roleChanged);
    }

    @Transactional
    public void deactivateStaff(Long restaurantId, Long userId) {
        if (userId.equals(TenantContext.getCurrentUserId())) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }
        User user = userRepository.findById(userId)
                .filter(u -> restaurantId.equals(u.getRestaurantId()) && !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));

        user.setIsActive(false);
        user.setTokenInvalidatedAt(System.currentTimeMillis());
        touch(user, System.currentTimeMillis());
        userRepository.save(user);
        log.info("Staff deactivated: userId={}", userId);
    }

    @Transactional
    public void activateStaff(Long restaurantId, Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> restaurantId.equals(u.getRestaurantId()) && !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));

        user.setIsActive(true);
        user.setTokenInvalidatedAt(null);
        touch(user, System.currentTimeMillis());
        userRepository.save(user);
        log.info("Staff activated: userId={}", userId);
    }

    // ─── Session revocation ──────────────────────────────────────────────────────

    /**
     * Signs one staff member out of every device without disabling their account.
     *
     * <p>Stamping {@code tokenInvalidatedAt} makes {@link
     * com.khanabook.saas.security.JwtRequestFilter} reject any token issued before now,
     * on the very next request. Use this when a device is lost or a session may be
     * compromised but the person should keep working — {@link #deactivateStaff} is the
     * heavier hammer that also locks the account.
     *
     * @return the number of sessions invalidated (1, or 0 if nothing changed)
     */
    @Transactional
    public int revokeStaffSessions(Long restaurantId, Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> restaurantId.equals(u.getRestaurantId()) && !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));

        long now = System.currentTimeMillis();
        user.setTokenInvalidatedAt(now);
        touch(user, now);
        userRepository.save(user);
        log.info("Sessions revoked: restaurantId={} userId={}", restaurantId, userId);
        return 1;
    }

    /**
     * Signs every member of the restaurant out of every device, including the caller.
     *
     * <p>The owner is deliberately included: the usual reason for reaching for this is a
     * lost or stolen terminal, and excluding the caller would leave the most privileged
     * session alive on it. Everyone signs in again afterwards.
     *
     * @return the number of accounts whose sessions were invalidated
     */
    @Transactional
    public int revokeAllSessions(Long restaurantId) {
        List<User> users = userRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        long now = System.currentTimeMillis();
        for (User user : users) {
            user.setTokenInvalidatedAt(now);
            touch(user, now);
        }
        userRepository.saveAll(users);
        log.warn("All sessions revoked: restaurantId={} accounts={}", restaurantId, users.size());
        return users.size();
    }

    // ─── Menu CRUD ───────────────────────────────────────────────────────────────

    @Transactional
    public MenuItem createMenuItem(Long restaurantId, CreateMenuItemRequest req) {
        validateMenuItemFields(req.name(), req.basePrice());
        Category category = requireCategory(restaurantId, req.categoryId());

        MenuItem item = new MenuItem();
        item.setName(req.name());
        item.setCategoryId(category.getId());
        item.setServerCategoryId(category.getId());
        item.setFoodType(req.foodType());
        item.setBasePrice(new java.math.BigDecimal(req.basePrice()));
        item.setDescription(req.description());
        if (req.imageUrl() != null && !req.imageUrl().isBlank()) {
            item.setImageUrl(req.imageUrl().trim());
        }
        item.setIsAvailable(true);
        item.setRestaurantId(restaurantId);
        item.setDeviceId("web-admin");
        item.setLocalId(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        item.setCreatedAt(now);
        touch(item, now);

        return menuItemRepository.save(item);
    }

    @Transactional
    public MenuItem updateMenuItem(Long restaurantId, Long menuItemId, UpdateMenuItemRequest req) {
        validateMenuItemFields(req.name(), req.basePrice());
        Category category = requireCategory(restaurantId, req.categoryId());

        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));

        item.setName(req.name());
        item.setCategoryId(category.getId());
        item.setServerCategoryId(category.getId());
        item.setFoodType(req.foodType());
        item.setBasePrice(new java.math.BigDecimal(req.basePrice()));
        item.setDescription(req.description());
        if (req.imageUrl() != null) {
            item.setImageUrl(req.imageUrl().trim().isEmpty() ? null : req.imageUrl().trim());
        }
        touch(item, System.currentTimeMillis());

        return menuItemRepository.save(item);
    }

    @Transactional
    public void deleteMenuItem(Long restaurantId, Long menuItemId) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));

        item.setIsDeleted(true);
        item.setIsAvailable(false);
        touch(item, System.currentTimeMillis());
        menuItemRepository.save(item);
    }

    @Transactional
    public MenuItem toggleMenuItemAvailability(Long restaurantId, Long menuItemId) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));

        item.setIsAvailable(!Boolean.TRUE.equals(item.getIsAvailable()));
        touch(item, System.currentTimeMillis());
        return menuItemRepository.save(item);
    }

    // ─── Terminal Reactivation ────────────────────────────────────────────────────

    @Transactional
    public void reactivateTerminal(Long restaurantId, Long terminalId) {
        profileRepository.findAndLockByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        RestaurantTerminal terminal = terminalRepository.findById(terminalId)
                .filter(t -> restaurantId.equals(t.getRestaurantId()))
                .orElseThrow(() -> new IllegalArgumentException("Terminal not found"));

        if (!"INACTIVE".equalsIgnoreCase(terminal.getStatus())) {
            throw new IllegalArgumentException("Terminal is not deactivated");
        }

        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        if (activeCount >= 5) {
            throw new ResponseStatusException(CONFLICT, "MAX_ACTIVE_TERMINALS_REACHED");
        }

        terminal.setStatus("ACTIVE");
        terminal.setIsActive(true);
        terminal.setCredentialVersion(
                (terminal.getCredentialVersion() != null ? terminal.getCredentialVersion() : 0) + 1);
        terminal.setUpdatedAt(System.currentTimeMillis());
        terminalRepository.save(terminal);
        log.info("Terminal reactivated: terminalId={}, restaurant={}", terminalId, restaurantId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private UserRole parseRole(String roleStr) {
        try {
            UserRole role = UserRole.valueOf(roleStr.toUpperCase());
            if (role == UserRole.KBOOK_ADMIN) {
                throw new IllegalArgumentException("Cannot assign KBOOK_ADMIN role via staff management");
            }
            return role;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("KBOOK_ADMIN")) throw e;
            throw new IllegalArgumentException(
                    "Invalid role: " + roleStr + ". Must be OWNER or SHOP_STAFF");
        }
    }

    /**
     * Release this phone/identifier from any soft-deleted user rows so the partial
     * unique indexes (phone_number / login_id / whatsapp_number) do not block
     * re-adding a previously-removed staff member. Non-destructive: the deleted
     * account and its history are preserved; only the reusable identifier columns
     * are detached (phone/whatsapp nulled, login_id tombstoned but still readable).
     * Mirrors AuthServiceImpl#releaseIdentifierFromDeletedUsers.
     */
    private void releaseIdentifierFromDeletedUsers(String phone) {
        var deleted = userRepository.findDeletedHoldingIdentifier(phone);
        if (deleted.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (User u : deleted) {
            if (phone.equalsIgnoreCase(u.getPhoneNumber())) {
                u.setPhoneNumber(null);
            }
            if (phone.equalsIgnoreCase(u.getWhatsappNumber())) {
                u.setWhatsappNumber(null);
            }
            if (phone.equalsIgnoreCase(u.getLoginId())) {
                u.setLoginId(u.getLoginId() + "|deleted:" + u.getId());
            }
            u.setUpdatedAt(now);
            u.setServerUpdatedAt(now);
        }
        userRepository.saveAll(deleted);
        userRepository.flush();
        log.info("Released staff identifier from {} soft-deleted user row(s)", deleted.size());
    }

    private void validateMenuItemFields(String name, String basePrice) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name is required");
        }
        try {
            double price = Double.parseDouble(basePrice);
            if (price <= 0) {
                throw new IllegalArgumentException("Base price must be greater than zero");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Base price must be a valid number");
        }
    }

    private Category requireCategory(Long restaurantId, Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category is required");
        }
        return categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

    private void touch(com.khanabook.saas.sync.entity.BaseSyncEntity entity, long requestedTime) {
        long previous = entity.getServerUpdatedAt() != null ? entity.getServerUpdatedAt() : 0L;
        long timestamp = Math.max(requestedTime, previous + 1);
        entity.setUpdatedAt(timestamp);
        entity.setServerUpdatedAt(timestamp);
    }
}
