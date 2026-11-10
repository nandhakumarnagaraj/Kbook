package com.khanabook.saas.feature.subscription.service;

import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.subscription.data.*;
import com.khanabook.saas.feature.subscription.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private static final long MS_PER_DAY = 24L * 60 * 60 * 1000;

    private final SubscriptionPlanRepository planRepository;
    private final RestaurantSubscriptionRepository subscriptionRepository;
    private final RestaurantTerminalRepository terminalRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionSummaryDTO getSubscriptionSummary(Long restaurantId) {
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant ID is required");
        }

        RestaurantSubscription sub = getOrCreateSubscription(restaurantId);
        SubscriptionPlan plan = sub.getPlan();

        long now = System.currentTimeMillis();
        long diffMs = sub.getCurrentPeriodEnd() - now;
        long daysRemaining = diffMs > 0 ? (long) Math.ceil((double) diffMs / MS_PER_DAY) : 0L;

        boolean isExpired = (sub.getStatus() == SubscriptionStatus.EXPIRED || sub.getStatus() == SubscriptionStatus.CANCELLED);
        boolean isInGracePeriod = (sub.getStatus() == SubscriptionStatus.GRACE_PERIOD);

        if (!isExpired && !isInGracePeriod && diffMs <= 0) {
            if (sub.getGracePeriodEndAt() != null && now <= sub.getGracePeriodEndAt()) {
                isInGracePeriod = true;
            } else {
                isExpired = true;
            }
        }

        long activeTerminals = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        long activeStaff = userRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId);

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("inventory", Boolean.TRUE.equals(plan.getInventoryEnabled()));
        features.put("ai_menu", Boolean.TRUE.equals(plan.getAiMenuEnabled()));
        features.put("online_payments", Boolean.TRUE.equals(plan.getOnlinePaymentsEnabled()));
        features.put("advanced_reports", Boolean.TRUE.equals(plan.getAdvancedReportsEnabled()));
        features.put("multi_terminal", plan.getMaxTerminals() != null && plan.getMaxTerminals() > 1);

        return SubscriptionSummaryDTO.builder()
                .restaurantId(restaurantId)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .planDescription(plan.getDescription())
                .status(sub.getStatus())
                .billingCycle(sub.getBillingCycle())
                .startedAt(sub.getStartedAt())
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .trialEndAt(sub.getTrialEndAt())
                .gracePeriodEndAt(sub.getGracePeriodEndAt())
                .daysRemaining(daysRemaining)
                .isExpired(isExpired)
                .isInGracePeriod(isInGracePeriod)
                .autoRenew(sub.getAutoRenew())
                .maxTerminals(plan.getMaxTerminals())
                .activeTerminalsCount((int) activeTerminals)
                .maxStaffMembers(plan.getMaxStaffMembers())
                .activeStaffCount((int) activeStaff)
                .inventoryEnabled(plan.getInventoryEnabled())
                .aiMenuEnabled(plan.getAiMenuEnabled())
                .onlinePaymentsEnabled(plan.getOnlinePaymentsEnabled())
                .advancedReportsEnabled(plan.getAdvancedReportsEnabled())
                .priceMonthly(plan.getPriceMonthly())
                .priceYearly(plan.getPriceYearly())
                .features(features)
                .notes(sub.getNotes())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getAvailablePlans() {
        return planRepository.findAllByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubscriptionSummaryDTO changePlan(Long restaurantId, ChangePlanRequestDTO request) {
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant ID is required");
        }
        if (request == null || request.getPlanCode() == null || request.getPlanCode().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan code is required");
        }

        String planCode = request.getPlanCode().trim().toUpperCase();
        SubscriptionPlan targetPlan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found: " + planCode));

        if (!Boolean.TRUE.equals(targetPlan.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The requested plan is currently inactive");
        }

        // Safety guard: check if active terminals exceed target plan's quota
        long activeTerminals = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        if (activeTerminals > targetPlan.getMaxTerminals()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Cannot switch to %s: you currently have %d active terminal(s), but this plan allows a maximum of %d. Please deactivate excess terminals first.",
                            targetPlan.getName(), activeTerminals, targetPlan.getMaxTerminals()));
        }

        RestaurantSubscription sub = subscriptionRepository.findByRestaurantIdWithLock(restaurantId)
                .orElseGet(() -> createDefaultSubscriptionEntity(restaurantId, targetPlan));

        long now = System.currentTimeMillis();
        BillingCycle cycle = request.getBillingCycle() != null ? request.getBillingCycle() : BillingCycle.MONTHLY;
        long durationDays = (cycle == BillingCycle.YEARLY) ? 365L : 30L;
        long periodEnd = now + (durationDays * MS_PER_DAY);
        long graceEnd = periodEnd + (7L * MS_PER_DAY); // 7 days grace for paid plans

        sub.setPlan(targetPlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingCycle(cycle);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setGracePeriodEndAt(graceEnd);
        sub.setAutoRenew(true);
        sub.setUpdatedAt(now);

        subscriptionRepository.save(sub);
        log.info("Subscription upgraded: restaurantId={}, plan={}, cycle={}", restaurantId, targetPlan.getCode(), cycle);

        return getSubscriptionSummary(restaurantId);
    }

    @Override
    @Transactional
    public RestaurantSubscription initializeTrialSubscription(Long restaurantId) {
        if (restaurantId == null) return null;

        var existing = subscriptionRepository.findByRestaurantId(restaurantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        SubscriptionPlan trialPlan = planRepository.findByCode("FREE_TRIAL")
                .orElseGet(this::getOrCreateDefaultPlan);

        long now = System.currentTimeMillis();
        long trialEnd = now + (14L * MS_PER_DAY);
        long graceEnd = trialEnd + (3L * MS_PER_DAY);

        RestaurantSubscription sub = new RestaurantSubscription();
        sub.setRestaurantId(restaurantId);
        sub.setPlan(trialPlan);
        sub.setStatus(SubscriptionStatus.TRIAL);
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setStartedAt(now);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(trialEnd);
        sub.setTrialEndAt(trialEnd);
        sub.setGracePeriodEndAt(graceEnd);
        sub.setAutoRenew(false);
        sub.setNotes("14-day free trial activated upon merchant onboarding");
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);

        RestaurantSubscription saved = subscriptionRepository.save(sub);
        log.info("Initialized 14-day trial subscription for restaurantId={}", restaurantId);
        return saved;
    }

    @Override
    @Transactional
    public RestaurantSubscription getOrCreateSubscription(Long restaurantId) {
        return subscriptionRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> {
                    SubscriptionPlan defaultPlan = getOrCreateDefaultPlan();
                    return createDefaultSubscriptionEntity(restaurantId, defaultPlan);
                });
    }

    private SubscriptionPlan getOrCreateDefaultPlan() {
        return planRepository.findByCode("PRO")
                .orElseGet(() -> planRepository.findAll().stream().findFirst()
                        .orElseGet(this::createFallbackPlan));
    }

    private SubscriptionPlan createFallbackPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PRO");
        plan.setName("Professional Plan");
        plan.setDescription("Auto-provisioned default plan");
        plan.setPriceMonthly(new java.math.BigDecimal("1299.00"));
        plan.setPriceYearly(new java.math.BigDecimal("12990.00"));
        plan.setMaxTerminals(5);
        plan.setMaxStaffMembers(10);
        plan.setInventoryEnabled(true);
        plan.setAiMenuEnabled(false);
        plan.setOnlinePaymentsEnabled(true);
        plan.setAdvancedReportsEnabled(true);
        plan.setIsActive(true);
        plan.setSortOrder(1);
        long now = System.currentTimeMillis();
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        try {
            return planRepository.save(plan);
        } catch (Exception e) {
            // In case of concurrent save or read-only context, query again
            return planRepository.findByCode("PRO").orElse(plan);
        }
    }

    private RestaurantSubscription createDefaultSubscriptionEntity(Long restaurantId, SubscriptionPlan plan) {
        long now = System.currentTimeMillis();
        long oneYear = now + (365L * MS_PER_DAY);

        RestaurantSubscription sub = new RestaurantSubscription();
        sub.setRestaurantId(restaurantId);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingCycle(BillingCycle.YEARLY);
        sub.setStartedAt(now);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(oneYear);
        sub.setAutoRenew(true);
        sub.setNotes("Auto-provisioned default plan");
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);

        return subscriptionRepository.save(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertTerminalLimit(Long restaurantId) {
        RestaurantSubscription sub = getOrCreateSubscription(restaurantId);
        SubscriptionPlan plan = sub.getPlan();
        int maxTerminals = plan.getMaxTerminals() != null ? plan.getMaxTerminals() : 1;
        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");

        if (activeCount >= maxTerminals) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("TERMINAL_LIMIT_REACHED: Your current plan (%s) allows up to %d active terminal(s). You currently have %d active. Upgrade your subscription to connect additional terminals.",
                            plan.getName(), maxTerminals, activeCount));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFeatureAllowed(Long restaurantId, String featureKey) {
        if (restaurantId == null || featureKey == null) return false;

        RestaurantSubscription sub = getOrCreateSubscription(restaurantId);
        if (sub.getStatus() == SubscriptionStatus.EXPIRED || sub.getStatus() == SubscriptionStatus.CANCELLED) {
            return false;
        }

        SubscriptionPlan plan = sub.getPlan();
        switch (featureKey.trim().toLowerCase()) {
            case "inventory":
                return Boolean.TRUE.equals(plan.getInventoryEnabled());
            case "ai_menu":
                return Boolean.TRUE.equals(plan.getAiMenuEnabled());
            case "online_payments":
                return Boolean.TRUE.equals(plan.getOnlinePaymentsEnabled());
            case "advanced_reports":
                return Boolean.TRUE.equals(plan.getAdvancedReportsEnabled());
            case "multi_terminal":
                return plan.getMaxTerminals() != null && plan.getMaxTerminals() > 1;
            default:
                return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void assertFeatureAllowed(Long restaurantId, String featureKey) {
        if (!isFeatureAllowed(restaurantId, featureKey)) {
            RestaurantSubscription sub = getOrCreateSubscription(restaurantId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    String.format("FEATURE_LOCKED: Feature '%s' is not included in your %s plan. Please upgrade your subscription.",
                            featureKey, sub.getPlan().getName()));
        }
    }

    @Override
    @Transactional
    public SubscriptionSummaryDTO adminUpdateSubscription(Long restaurantId, AdminSubscriptionUpdateRequestDTO request) {
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant ID is required");
        }

        RestaurantSubscription sub = subscriptionRepository.findByRestaurantIdWithLock(restaurantId)
                .orElseGet(() -> getOrCreateSubscription(restaurantId));

        long now = System.currentTimeMillis();

        if (request.getPlanCode() != null && !request.getPlanCode().isBlank()) {
            SubscriptionPlan plan = planRepository.findByCode(request.getPlanCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found: " + request.getPlanCode()));
            sub.setPlan(plan);
        }

        if (request.getStatus() != null) {
            sub.setStatus(request.getStatus());
        }

        if (request.getBillingCycle() != null) {
            sub.setBillingCycle(request.getBillingCycle());
        }

        if (request.getExtendDays() != null && request.getExtendDays() > 0) {
            long currentEnd = Math.max(now, sub.getCurrentPeriodEnd());
            long newEnd = currentEnd + (request.getExtendDays() * MS_PER_DAY);
            sub.setCurrentPeriodEnd(newEnd);
            sub.setGracePeriodEndAt(newEnd + (7L * MS_PER_DAY));
            if (sub.getStatus() == SubscriptionStatus.EXPIRED || sub.getStatus() == SubscriptionStatus.GRACE_PERIOD) {
                sub.setStatus(SubscriptionStatus.ACTIVE);
            }
        }

        if (request.getAutoRenew() != null) {
            sub.setAutoRenew(request.getAutoRenew());
        }

        if (request.getNotes() != null) {
            sub.setNotes(request.getNotes());
        }

        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);
        log.info("Admin updated subscription for restaurantId={}, newStatus={}", restaurantId, sub.getStatus());

        return getSubscriptionSummary(restaurantId);
    }

    @Override
    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void evaluateSubscriptionExpirations() {
        long now = System.currentTimeMillis();
        List<SubscriptionStatus> activeStatuses = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);
        List<RestaurantSubscription> expiredList = subscriptionRepository.findAllByStatusInAndCurrentPeriodEndBefore(
                activeStatuses, now);

        for (RestaurantSubscription sub : expiredList) {
            if (sub.getGracePeriodEndAt() != null && now <= sub.getGracePeriodEndAt()) {
                sub.setStatus(SubscriptionStatus.GRACE_PERIOD);
                sub.setUpdatedAt(now);
                log.info("Subscription entered GRACE_PERIOD for restaurantId={}", sub.getRestaurantId());
            } else {
                sub.setStatus(SubscriptionStatus.EXPIRED);
                sub.setUpdatedAt(now);
                log.info("Subscription marked EXPIRED for restaurantId={}", sub.getRestaurantId());
            }
            subscriptionRepository.save(sub);
        }
    }

    private SubscriptionPlanDTO toDto(SubscriptionPlan entity) {
        return SubscriptionPlanDTO.builder()
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .priceMonthly(entity.getPriceMonthly())
                .priceYearly(entity.getPriceYearly())
                .maxTerminals(entity.getMaxTerminals())
                .maxStaffMembers(entity.getMaxStaffMembers())
                .inventoryEnabled(entity.getInventoryEnabled())
                .aiMenuEnabled(entity.getAiMenuEnabled())
                .onlinePaymentsEnabled(entity.getOnlinePaymentsEnabled())
                .advancedReportsEnabled(entity.getAdvancedReportsEnabled())
                .isActive(entity.getIsActive())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
