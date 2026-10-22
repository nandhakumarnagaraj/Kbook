package com.khanabook.saas.feature.business.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.core.utility.PricingConstants;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.inventory.data.PurchaseOrder;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderItem;
import com.khanabook.saas.feature.inventory.data.StockMovement;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.Vendor;
import com.khanabook.saas.feature.inventory.data.CustomerProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO;
import com.khanabook.saas.feature.sync.data.SyncMapper;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.Chargeback;
import com.khanabook.saas.feature.notifications.data.NotificationEvent;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.staff.data.StaffPermission;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.platform.data.FeatureFlag;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.core.exception.DuplicateStaffPhoneException;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.staff.service.PermissionService;
import com.khanabook.saas.feature.business.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class BusinessWriteService {

    private static final Logger log = LoggerFactory.getLogger(BusinessWriteService.class);
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Staff passwords are read off a WhatsApp message and typed on a phone keypad,
     * so the generator favours unambiguous characters over maximum entropy: no
     * 0/O, 1/l/I. Ambiguity here costs real support calls, and the value is only
     * ever a starting credential that the staff member can change.
     */
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final String PW_LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String PW_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PW_DIGIT = "23456789";
    private static final int PW_LENGTH = 10;

    private static String generateStaffPassword() {
        String all = PW_LOWER + PW_UPPER + PW_DIGIT;
        StringBuilder sb = new StringBuilder(PW_LENGTH);
        // Guarantee one character from each class so the value cannot degrade into
        // an all-lowercase word that is trivial to guess or type.
        sb.append(PW_LOWER.charAt(PASSWORD_RANDOM.nextInt(PW_LOWER.length())));
        sb.append(PW_UPPER.charAt(PASSWORD_RANDOM.nextInt(PW_UPPER.length())));
        sb.append(PW_DIGIT.charAt(PASSWORD_RANDOM.nextInt(PW_DIGIT.length())));
        while (sb.length() < PW_LENGTH) {
            sb.append(all.charAt(PASSWORD_RANDOM.nextInt(all.length())));
        }
        // Shuffle so the guaranteed characters are not always in the first 3 slots.
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = PASSWORD_RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final RestaurantTerminalRepository terminalRepository;
    private final RestaurantProfileRepository profileRepository;
    private final PermissionService permissionService;
    private final com.khanabook.saas.feature.auth.service.PasswordResetOtpService passwordResetOtpService;
    private final com.khanabook.saas.feature.auth.repository.RefreshTokenRepository refreshTokenRepository;
    private final PushNotificationService pushNotificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public BusinessWriteService(UserRepository userRepository,
                                CategoryRepository categoryRepository,
                                MenuItemRepository menuItemRepository,
                                ItemVariantRepository itemVariantRepository,
                                RestaurantTerminalRepository terminalRepository,
                                RestaurantProfileRepository profileRepository,
                                PermissionService permissionService,
                                com.khanabook.saas.feature.auth.service.PasswordResetOtpService passwordResetOtpService,
                                com.khanabook.saas.feature.auth.repository.RefreshTokenRepository refreshTokenRepository,
                                PushNotificationService pushNotificationService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.itemVariantRepository = itemVariantRepository;
        this.terminalRepository = terminalRepository;
        this.profileRepository = profileRepository;
        this.permissionService = permissionService;
        this.passwordResetOtpService = passwordResetOtpService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.pushNotificationService = pushNotificationService;
    }

    // ─── Staff CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public StaffCreatedResponse createStaff(Long restaurantId, CreateStaffRequest req) {
        UserRole role = parseRole(req.role());

        // Fix #2 (issues.txt): canonicalize the phone before any lookup or write so
        // +91 / leading-0 / spaced variants cannot create format-variant duplicates.
        req = new CreateStaffRequest(req.name(), com.khanabook.saas.core.utility.PhoneNormalizer.normalize(req.phone()), req.role(), req.email(), req.permissions());
        // Only a LIVE (not soft-deleted) account blocks re-adding this phone.
        // Soft-deleted staff must not reserve the number forever — mirrors signup
        // (AuthServiceImpl#ensurePhoneNumberAvailableForSignup).
        if (userRepository.findActiveByAnyIdentifier(req.phone()).isPresent()) {
            throw new DuplicateStaffPhoneException();
        }
        // Release the identifier from any soft-deleted rows so the partial unique
        // indexes (phone_number / login_id / whatsapp_number) don't block reuse.
        releaseIdentifierFromDeletedUsers(req.phone());

        // Onboarding: the system generates a password and sends it to the staff
        // member on WhatsApp. They sign in with it and can change it from the app
        // at any time. This replaces the previous OTP-only flow, whose
        // staff-invite challenge was written under a namespace that had no
        // validator and no consuming endpoint, so the code could never be redeemed.
        String generatedPassword = generateStaffPassword();
        String hash = passwordEncoder.encode(generatedPassword);

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

// Deliver the generated password straight to the staff member. Never block staff
        // creation if the send fails — the account is still usable via "Re-send
        // password" from the staff list, or Forgot Password in the app.
        boolean credentialsSent;
        try {
            passwordResetOtpService.sendStaffCredentials(saved.getPhoneNumber(), generatedPassword);
            credentialsSent = true;
        } catch (RuntimeException e) {
            log.warn("Staff created but credentials send failed for userId={}: {}",
                    saved.getId(), e.getMessage());
            credentialsSent = false;
        }

        return new StaffCreatedResponse(
                saved.getId(), saved.getName(), saved.getPhoneNumber(),
                saved.getRole().name(), credentialsSent
        );
    }

    /**
     * Issues a fresh password for a staff member and sends it to their phone.
     *
     * <p>Needed because a generated password is delivered over WhatsApp, which is
     * lossy: the message can fail, or be missed. Mirrors
     * {@code AuthServiceImpl#resetPassword} on the security side — the hash is
     * replaced, tokens issued under the previous password are invalidated, and all
     * refresh tokens are revoked, so a leaked first password stops working the
     * moment the owner re-sends.
     */
    @Transactional
    public StaffCredentialsResponse resendStaffCredentials(Long restaurantId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        // Tenant check before any role check so a cross-tenant id is indistinguishable
        // from a missing one.
        if (!restaurantId.equals(user.getRestaurantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found");
        }
        if (user.getRole() == UserRole.OWNER || user.getRole() == UserRole.KBOOK_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This account is not a staff account");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This staff account is deactivated. Activate it first.");
        }

        String generatedPassword = generateStaffPassword();
        long now = System.currentTimeMillis();
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        user.setTokenInvalidatedAt(now);
        user.setUpdatedAt(now);
        user.setServerUpdatedAt(now);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());

        passwordResetOtpService.sendStaffCredentials(user.getPhoneNumber(), generatedPassword);
        log.info("Re-sent generated password for staff userId={} restaurant={} — sessions revoked",
                user.getId(), restaurantId);

        return new StaffCredentialsResponse(user.getId(), user.getPhoneNumber());
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
     * com.khanabook.saas.core.security.JwtRequestFilter} reject any token issued before now,
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

        MenuItem saved = menuItemRepository.save(item);
        // Near-real-time propagation, same contract as MenuItemServiceImpl: the web admin
        // writes the rows the tills sync, so without this nudge an edit here waits for the
        // next periodic sync window instead of landing within seconds.
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
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

        // A move between categories lands on the tills through this same nudge, so the
        // dish leaves its old category in the POS within seconds rather than minutes.
        MenuItem saved = menuItemRepository.save(item);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
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
        pushNotificationService.pushSyncNow(restaurantId);
    }

    @Transactional
    public MenuItem toggleMenuItemAvailability(Long restaurantId, Long menuItemId) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));

        item.setIsAvailable(!Boolean.TRUE.equals(item.getIsAvailable()));
        touch(item, System.currentTimeMillis());
        MenuItem saved = menuItemRepository.save(item);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
    }

    /**
     * Pin or unpin a dish. Owner-only (enforced at the controller), and deliberately the ONLY
     * writer of {@code menuitems.is_favourite}: every dish write reaches the tills as a sync
     * push, and {@code GenericSyncService} restores the stored value on both push paths, so a
     * till can neither set the pin nor clear it. A counter's own shortcuts are device-local
     * and never travel — this column is the shop's list, not one terminal's.
     */
    @Transactional
    public MenuItem setMenuItemFavourite(Long restaurantId, Long menuItemId, boolean favourite) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        if (Boolean.TRUE.equals(item.getIsFavourite()) == favourite) {
            // Idempotent no-op, same contract as setCategoryFavourite: re-sending the same
            // star must not publish a new server version, because every version bump wakes
            // every terminal for nothing.
            return item;
        }
        item.setIsFavourite(favourite);
        // touch() keeps serverUpdatedAt strictly increasing, so two toggles inside the same
        // millisecond cannot collide and be missed by a device's `> lastSyncTimestamp` pull.
        touch(item, System.currentTimeMillis());
        MenuItem saved = menuItemRepository.save(item);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
    }

    // ─── Category CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public Category createCategory(Long restaurantId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        long now = System.currentTimeMillis();
        Category category = new Category();
        category.setName(name.trim());
        category.setRestaurantId(restaurantId);
        category.setIsVeg(false);
        category.setIsActive(true);
        category.setIsDeleted(false);
        category.setDeviceId("web-admin");
        category.setLocalId(now);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        category.setSortOrder((int) categoryRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId));
        Category saved = categoryRepository.save(category);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
    }

    @Transactional
    public Category updateCategory(Long restaurantId, Long categoryId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        Category category = categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        long now = System.currentTimeMillis();
        category.setName(name.trim());
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        Category saved = categoryRepository.save(category);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
    }

    /**
     * Pin or unpin a category. Owner-only (enforced at the controller), and deliberately the
     * ONLY writer of is_favourite: every menu and category write reaches the tills as a
     * whole-record sync push, so a field a terminal does not know about has to be restored
     * server-side instead of round-tripped. See GenericSyncService.preserveServerOwnedState.
     */
    @Transactional
    public Category setCategoryFavourite(Long restaurantId, Long categoryId, boolean favourite) {
        Category category = categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (Boolean.TRUE.equals(category.getIsFavourite()) == favourite) {
            // Idempotent no-op: re-sending the same star must not publish a new server
            // version, which would push a pointless wake-up to every terminal.
            return category;
        }
        category.setIsFavourite(favourite);
        // touch() keeps serverUpdatedAt strictly increasing, so two toggles inside the same
        // millisecond cannot collide and get missed by a device's `> lastSyncTimestamp` pull.
        touch(category, System.currentTimeMillis());
        Category saved = categoryRepository.save(category);
        pushNotificationService.pushSyncNow(restaurantId);
        return saved;
    }

    @Transactional
    public void deleteCategory(Long restaurantId, Long categoryId) {
        Category category = categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        boolean hasItems = menuItemRepository.existsByRestaurantIdAndCategoryIdAndIsDeletedFalse(restaurantId, categoryId);
        if (hasItems) {
            throw new IllegalStateException("Cannot delete category containing active menu items. Reassign or delete the items first.");
        }
        long now = System.currentTimeMillis();
        category.setIsDeleted(true);
        category.setUpdatedAt(now);
        category.setServerUpdatedAt(now);
        categoryRepository.save(category);
        pushNotificationService.pushSyncNow(restaurantId);
    }

    // ─── Variant CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public ItemVariant createVariant(Long restaurantId, Long menuItemId, String variantName, java.math.BigDecimal price) {
        if (variantName == null || variantName.trim().isEmpty()) {
            throw new IllegalArgumentException("Variant name is required");
        }
        if (price == null || price.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid price is required");
        }
        MenuItem item = menuItemRepository.findById(menuItemId)
                .filter(m -> restaurantId.equals(m.getRestaurantId()) && !Boolean.TRUE.equals(m.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));

        long now = System.currentTimeMillis();
        ItemVariant variant = new ItemVariant();
        variant.setRestaurantId(restaurantId);
        variant.setMenuItemId(menuItemId);
        variant.setServerMenuItemId(menuItemId);
        variant.setVariantName(variantName.trim());
        variant.setPrice(price);
        variant.setIsAvailable(true);
        variant.setIsDeleted(false);
        variant.setDeviceId("web-admin");
        variant.setLocalId(now);
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        variant.setServerUpdatedAt(now);
        variant.setSortOrder((int) itemVariantRepository.countByMenuItemIdAndIsDeletedFalse(menuItemId));

        ItemVariant saved = itemVariantRepository.save(variant);
        menuItemRepository.recomputeHasVariantsFlag(java.util.List.of(menuItemId), restaurantId, now);
        return saved;
    }

    @Transactional
    public void deleteVariant(Long restaurantId, Long menuItemId, Long variantId) {
        ItemVariant variant = itemVariantRepository.findById(variantId)
                .filter(v -> restaurantId.equals(v.getRestaurantId())
                        && menuItemId.equals(v.getMenuItemId())
                        && !Boolean.TRUE.equals(v.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Variant not found"));

        long now = System.currentTimeMillis();
        variant.setIsDeleted(true);
        variant.setUpdatedAt(now);
        variant.setServerUpdatedAt(now);
        itemVariantRepository.save(variant);
        menuItemRepository.recomputeHasVariantsFlag(java.util.List.of(menuItemId), restaurantId, now);
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
            // This only checked the floor, so the Rs. 1,00,000 cap was not enforced on the
            // web-admin create or update path at all.
            if (java.math.BigDecimal.valueOf(price).compareTo(PricingConstants.MAX_ITEM_PRICE) > 0) {
                throw new IllegalArgumentException("Base price must be between Rs. 0 and Rs. 1,00,000");
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

    private void touch(com.khanabook.saas.feature.sync.data.BaseSyncEntity entity, long requestedTime) {
        long previous = entity.getServerUpdatedAt() != null ? entity.getServerUpdatedAt() : 0L;
        long timestamp = Math.max(requestedTime, previous + 1);
        entity.setUpdatedAt(timestamp);
        entity.setServerUpdatedAt(timestamp);
    }

    @Transactional
    public RestaurantProfileDTO updateProfile(Long restaurantId, RestaurantProfileDTO updateDto) {
        RestaurantProfile profile = profileRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> {
                    RestaurantProfile p = new RestaurantProfile();
                    p.setRestaurantId(restaurantId);
                    p.setCreatedAt(System.currentTimeMillis());
                    return p;
                });

        if (updateDto.getShopName() != null) profile.setShopName(updateDto.getShopName());
        if (updateDto.getShopAddress() != null) profile.setShopAddress(updateDto.getShopAddress());
        if (updateDto.getWhatsappNumber() != null) profile.setWhatsappNumber(updateDto.getWhatsappNumber());
        if (updateDto.getEmail() != null) profile.setEmail(updateDto.getEmail());
        if (updateDto.getGstEnabled() != null) profile.setGstEnabled(updateDto.getGstEnabled());
        if (updateDto.getGstin() != null) profile.setGstin(updateDto.getGstin());
        if (updateDto.getGstPercentage() != null) profile.setGstPercentage(updateDto.getGstPercentage());
        if (updateDto.getCustomTaxName() != null) profile.setCustomTaxName(updateDto.getCustomTaxName());
        if (updateDto.getCustomTaxPercentage() != null) profile.setCustomTaxPercentage(updateDto.getCustomTaxPercentage());
        if (updateDto.getUpiEnabled() != null) profile.setUpiEnabled(updateDto.getUpiEnabled());
        if (updateDto.getUpiHandle() != null) profile.setUpiHandle(updateDto.getUpiHandle());
        if (updateDto.getUpiMobile() != null) profile.setUpiMobile(updateDto.getUpiMobile());
        if (updateDto.getCashEnabled() != null) profile.setCashEnabled(updateDto.getCashEnabled());
        if (updateDto.getPosEnabled() != null) profile.setPosEnabled(updateDto.getPosEnabled());
        if (updateDto.getOrderPaymentFlowMode() != null) profile.setOrderPaymentFlowMode(updateDto.getOrderPaymentFlowMode());
        if (updateDto.getInvoiceFooter() != null) profile.setInvoiceFooter(updateDto.getInvoiceFooter());
        if (updateDto.getReviewUrl() != null) profile.setReviewUrl(updateDto.getReviewUrl());
        if (updateDto.getFssaiNumber() != null) profile.setFssaiNumber(updateDto.getFssaiNumber());
        if (updateDto.getFssaiExpiryDate() != null) {
            try {
                profile.setFssaiExpiryDate(updateDto.getFssaiExpiryDate().isBlank() ? null : java.time.LocalDate.parse(updateDto.getFssaiExpiryDate().trim()));
            } catch (Exception e) {
                log.warn("Invalid fssaiExpiryDate format '{}' for restaurant {}: {}", updateDto.getFssaiExpiryDate(), restaurantId, e.getMessage());
            }
        }
        if (updateDto.getGstExpiryDate() != null) {
            try {
                profile.setGstExpiryDate(updateDto.getGstExpiryDate().isBlank() ? null : java.time.LocalDate.parse(updateDto.getGstExpiryDate().trim()));
            } catch (Exception e) {
                log.warn("Invalid gstExpiryDate format '{}' for restaurant {}: {}", updateDto.getGstExpiryDate(), restaurantId, e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        // Per-field revisions: stamp only the fields this update actually wrote, so a till
        // editing other fields is not rejected as stale.
        java.util.List<String> written = new java.util.ArrayList<>();
        if (updateDto.getShopName() != null) written.add("shopname");
        if (updateDto.getShopAddress() != null) written.add("shopaddress");
        if (updateDto.getWhatsappNumber() != null) written.add("whatsappnumber");
        if (updateDto.getEmail() != null) written.add("email");
        if (updateDto.getGstEnabled() != null) written.add("gstenabled");
        if (updateDto.getGstin() != null) written.add("gstin");
        if (updateDto.getGstPercentage() != null) written.add("gstpercentage");
        if (updateDto.getCustomTaxName() != null) written.add("customtaxname");
        if (updateDto.getCustomTaxPercentage() != null) written.add("customtaxpercentage");
        if (updateDto.getUpiEnabled() != null) written.add("upienabled");
        if (updateDto.getUpiHandle() != null) written.add("upihandle");
        if (updateDto.getUpiMobile() != null) written.add("upimobile");
        if (updateDto.getCashEnabled() != null) written.add("cashenabled");
        if (updateDto.getPosEnabled() != null) written.add("posenabled");
        if (updateDto.getOrderPaymentFlowMode() != null) written.add("orderpaymentflowmode");
        if (updateDto.getInvoiceFooter() != null) written.add("invoicefooter");
        if (updateDto.getReviewUrl() != null) written.add("reviewurl");
        if (updateDto.getFssaiNumber() != null) written.add("fssainumber");
        if (updateDto.getFssaiExpiryDate() != null) written.add("fssaiexpirydate");
        if (updateDto.getGstExpiryDate() != null) written.add("gstexpirydate");
        if (!written.isEmpty()) {
            profile.markFieldsChanged(now, written.toArray(new String[0]));
        }
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        RestaurantProfile saved = profileRepository.save(profile);
        return SyncMapper.map(saved, RestaurantProfileDTO.class);
    }
}
