package com.khanabook.saas.feature.subscription.controller;

import com.khanabook.saas.feature.subscription.dto.AdminSubscriptionUpdateRequestDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionSummaryDTO;
import com.khanabook.saas.feature.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Superadmin subscription management endpoints.
 * Secured under /admin/** (requires KBOOK_ADMIN role).
 */
@RestController
@RequestMapping("/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{restaurantId}")
    public ResponseEntity<SubscriptionSummaryDTO> getRestaurantSubscription(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionSummary(restaurantId));
    }

    @PostMapping("/{restaurantId}/override")
    public ResponseEntity<SubscriptionSummaryDTO> overrideSubscription(
            @PathVariable Long restaurantId,
            @RequestBody AdminSubscriptionUpdateRequestDTO request) {
        return ResponseEntity.ok(subscriptionService.adminUpdateSubscription(restaurantId, request));
    }
}
