package com.khanabook.saas.feature.subscription.service;

import com.khanabook.saas.feature.subscription.data.RestaurantSubscription;
import com.khanabook.saas.feature.subscription.dto.AdminSubscriptionUpdateRequestDTO;
import com.khanabook.saas.feature.subscription.dto.ChangePlanRequestDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionPlanDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionSummaryDTO;

import java.util.List;

public interface SubscriptionService {

    /**
     * Retrieves the current subscription summary and active entitlements for a restaurant.
     * Guarantees non-null return via automatic fallback/self-healing.
     */
    SubscriptionSummaryDTO getSubscriptionSummary(Long restaurantId);

    /**
     * Lists all active subscription plans available for purchase or upgrade.
     */
    List<SubscriptionPlanDTO> getAvailablePlans();

    /**
     * Upgrades or switches the subscription plan for a restaurant.
     */
    SubscriptionSummaryDTO changePlan(Long restaurantId, ChangePlanRequestDTO request);

    /**
     * Initializes a 14-day full FREE_TRIAL subscription for a newly onboarded restaurant.
     */
    RestaurantSubscription initializeTrialSubscription(Long restaurantId);

    /**
     * Returns the existing subscription or safely initializes one if missing.
     */
    RestaurantSubscription getOrCreateSubscription(Long restaurantId);

    /**
     * Enforces the plan's terminal quota. Throws CONFLICT if active terminals >= max allowed.
     */
    void assertTerminalLimit(Long restaurantId);

    /**
     * Checks if a particular feature key is permitted under the active subscription plan.
     */
    boolean isFeatureAllowed(Long restaurantId, String featureKey);

    /**
     * Throws FORBIDDEN if the specified feature is locked for the restaurant's subscription.
     */
    void assertFeatureAllowed(Long restaurantId, String featureKey);

    /**
     * Administrative override to change plan, status, or extend trial period.
     */
    SubscriptionSummaryDTO adminUpdateSubscription(Long restaurantId, AdminSubscriptionUpdateRequestDTO request);

    /**
     * Scheduled job evaluating expirations and managing grace periods.
     */
    void evaluateSubscriptionExpirations();
}
