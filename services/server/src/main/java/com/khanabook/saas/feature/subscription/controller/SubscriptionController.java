package com.khanabook.saas.feature.subscription.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.subscription.dto.ChangePlanRequestDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionPlanDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionSummaryDTO;
import com.khanabook.saas.feature.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Merchant subscription and plan management endpoints.
 * Secured under /business/** (requires OWNER role, tenant scoped via TenantContext).
 */
@RestController
@RequestMapping("/business/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/summary")
    public ResponseEntity<SubscriptionSummaryDTO> getSubscriptionSummary() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing tenant context");
        }
        return ResponseEntity.ok(subscriptionService.getSubscriptionSummary(restaurantId));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAvailablePlans() {
        return ResponseEntity.ok(subscriptionService.getAvailablePlans());
    }

    @PostMapping("/change-plan")
    public ResponseEntity<SubscriptionSummaryDTO> changePlan(@Valid @RequestBody ChangePlanRequestDTO request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing tenant context");
        }
        return ResponseEntity.ok(subscriptionService.changePlan(restaurantId, request));
    }
}
