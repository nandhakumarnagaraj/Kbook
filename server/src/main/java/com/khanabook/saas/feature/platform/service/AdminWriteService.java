package com.khanabook.saas.feature.platform.service;

import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.platform.dto.AdminBusinessDetailResponse;
import com.khanabook.saas.feature.platform.dto.AdminCreateBusinessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AdminWriteService {

    private static final Logger log = LoggerFactory.getLogger(AdminWriteService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService securityAuditService;
    private final AdminReadService adminReadService;

    public AdminWriteService(RestaurantProfileRepository profileRepository) {
        this(profileRepository, null, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminWriteService(RestaurantProfileRepository profileRepository,
                             UserRepository userRepository,
                             CategoryRepository categoryRepository,
                             PasswordEncoder passwordEncoder,
                             SecurityAuditService securityAuditService,
                             AdminReadService adminReadService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityAuditService = securityAuditService;
        this.adminReadService = adminReadService;
    }

    @Transactional
    public AdminBusinessDetailResponse createBusiness(AdminCreateBusinessRequest request) {
        String phone = request.getOwnerPhone().trim();
        userRepository.findActiveByAnyIdentifier(phone).ifPresent(u -> {
            throw new IllegalArgumentException("Phone number already in use by another active user: " + phone);
        });

        // Release identifier if held by soft-deleted user
        List<User> deletedHolding = userRepository.findDeletedHoldingIdentifier(phone);
        if (!deletedHolding.isEmpty()) {
            for (User u : deletedHolding) {
                u.setPhoneNumber(null);
                u.setLoginId("del_" + u.getId() + "_" + System.currentTimeMillis());
                u.setWhatsappNumber(null);
                userRepository.save(u);
            }
            userRepository.flush();
        }

        // Generate a positive unique restaurantId (6-9 digits)
        Long restaurantId = 100000L + (long) (RANDOM.nextDouble() * 899999L);
        while (profileRepository.findByRestaurantId(restaurantId).isPresent()) {
            restaurantId = 100000L + (long) (RANDOM.nextDouble() * 899999L);
        }

        long now = System.currentTimeMillis();
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

        // 1. Create Restaurant Profile
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(restaurantId);
        profile.setShopName(request.getShopName().trim());
        profile.setShopAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        profile.setWhatsappNumber(phone);
        profile.setCurrency("INR");
        profile.setIsSuspended(false);
        profile.setLastResetDate(today);
        profile.setLastResetDateProper(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        profile = profileRepository.saveAndFlush(profile);

        // 2. Create Owner User
        User owner = new User();
        owner.setName(request.getOwnerName().trim());
        owner.setPhoneNumber(phone);
        owner.setLoginId(phone);
        owner.setWhatsappNumber(phone);
        owner.setEmail(request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank() ? request.getOwnerEmail().trim() : null);
        owner.setAuthProvider(AuthProvider.PHONE);
        owner.setPasswordHash(passwordEncoder.encode(request.getInitialPassword()));
        owner.setRestaurantId(restaurantId);
        owner.setRole(UserRole.OWNER);
        owner.setIsActive(true);
        owner.setLocalId(1L);
        owner.setCreatedAt(now);
        owner.setUpdatedAt(now);
        owner.setServerUpdatedAt(now);
        userRepository.saveAndFlush(owner);

        // 3. Seed default core categories
        List<String> defaultCategories = List.of("Beverages", "Snacks", "Main Course");
        for (String catName : defaultCategories) {
            Category cat = new Category();
            cat.setRestaurantId(restaurantId);
            cat.setName(catName);
            cat.setIsVeg(true);
            cat.setIsActive(true);
            cat.setCreatedAt(now);
            cat.setUpdatedAt(now);
            cat.setServerUpdatedAt(now);
            categoryRepository.save(cat);
        }
        categoryRepository.flush();

        securityAuditService.record("BUSINESS_PROVISIONED", "success", "restaurant:" + restaurantId, null);
        log.info("Business provisioned by KBOOK_ADMIN: restaurantId={}, name={}", restaurantId, profile.getShopName());

        return adminReadService.getBusinessDetail(restaurantId);
    }

    @Transactional
    public void suspendBusiness(Long restaurantId) {
        RestaurantProfile profile = profileRepository
                .findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        profile.setIsSuspended(true);
        profile.setUpdatedAt(System.currentTimeMillis());
        profileRepository.save(profile);
        if (securityAuditService != null) {
            securityAuditService.record("BUSINESS_SUSPENDED", "success", "restaurant:" + restaurantId, null);
        }
        log.info("Business suspended: restaurantId={}", restaurantId);
    }

    @Transactional
    public void activateBusiness(Long restaurantId) {
        RestaurantProfile profile = profileRepository
                .findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        profile.setIsSuspended(false);
        profile.setUpdatedAt(System.currentTimeMillis());
        profileRepository.save(profile);
        if (securityAuditService != null) {
            securityAuditService.record("BUSINESS_ACTIVATED", "success", "restaurant:" + restaurantId, null);
        }
        log.info("Business activated: restaurantId={}", restaurantId);
    }
}
