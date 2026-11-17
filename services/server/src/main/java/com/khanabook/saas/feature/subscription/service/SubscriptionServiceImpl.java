package com.khanabook.saas.feature.subscription.service;

import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.subscription.data.*;
import com.khanabook.saas.feature.subscription.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private static final int MAX_FREE_TERMINALS = 10;
    private static final int MAX_FREE_STAFF = 50;

    private final RestaurantTerminalRepository terminalRepository;

    @Override
    public SubscriptionSummaryDTO getSubscriptionSummary(Long restaurantId) {
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant ID is required");
        }

        long activeTerminals = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("inventory", true);
        features.put("ai_menu", true);
        features.put("online_payments", true);
        features.put("advanced_reports", true);
        features.put("multi_terminal", true);

        return SubscriptionSummaryDTO.builder()
                .restaurantId(restaurantId)
                .planCode("FREE_LIFETIME")
                .planName("KhanaBook Free Lifetime")
                .planDescription("Universal 100% Free Lifetime access with all features unlocked.")
                .status(SubscriptionStatus.ACTIVE)
                .billingCycle(BillingCycle.LIFETIME)
                .startedAt(0L)
                .currentPeriodStart(0L)
                .currentPeriodEnd(Long.MAX_VALUE)
                .trialEndAt(null)
                .gracePeriodEndAt(null)
                .daysRemaining(9999L)
                .isExpired(false)
                .isInGracePeriod(false)
                .autoRenew(false)
                .maxTerminals(MAX_FREE_TERMINALS)
                .activeTerminalsCount((int) activeTerminals)
                .maxStaffMembers(MAX_FREE_STAFF)
                .activeStaffCount(0)
                .features(features)
                .build();
    }

    @Override
    public List<SubscriptionPlanDTO> getAvailablePlans() {
        return List.of(
                SubscriptionPlanDTO.builder()
                        .code("FREE_LIFETIME")
                        .name("KhanaBook Free Lifetime")
                        .description("Universal 100% Free Lifetime access with all features unlocked.")
                        .priceMonthly(BigDecimal.ZERO)
                        .priceYearly(BigDecimal.ZERO)
                        .maxTerminals(MAX_FREE_TERMINALS)
                        .maxStaffMembers(MAX_FREE_STAFF)
                        .inventoryEnabled(true)
                        .aiMenuEnabled(true)
                        .onlinePaymentsEnabled(true)
                        .advancedReportsEnabled(true)
                        .isActive(true)
                        .sortOrder(1)
                        .build()
        );
    }

    @Override
    public SubscriptionSummaryDTO changePlan(Long restaurantId, ChangePlanRequestDTO request) {
        return getSubscriptionSummary(restaurantId);
    }

    @Override
    public RestaurantSubscription initializeTrialSubscription(Long restaurantId) {
        return getOrCreateSubscription(restaurantId);
    }

    @Override
    public RestaurantSubscription getOrCreateSubscription(Long restaurantId) {
        RestaurantSubscription sub = new RestaurantSubscription();
        sub.setRestaurantId(restaurantId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingCycle(BillingCycle.LIFETIME);
        sub.setStartedAt(0L);
        sub.setCurrentPeriodStart(0L);
        sub.setCurrentPeriodEnd(Long.MAX_VALUE);
        return sub;
    }

    @Override
    public void assertTerminalLimit(Long restaurantId) {
        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        if (activeCount >= MAX_FREE_TERMINALS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TERMINAL_LIMIT_REACHED");
        }
    }

    @Override
    public boolean isFeatureAllowed(Long restaurantId, String featureKey) {
        return true; // All features are universally unlocked
    }

    @Override
    public void assertFeatureAllowed(Long restaurantId, String featureKey) {
        // Universal free access: all features allowed
    }

    @Override
    public SubscriptionSummaryDTO adminUpdateSubscription(Long restaurantId, AdminSubscriptionUpdateRequestDTO request) {
        return getSubscriptionSummary(restaurantId);
    }

    @Override
    public void evaluateSubscriptionExpirations() {
        // Universal free access never expires
    }
}
