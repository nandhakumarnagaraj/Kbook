package com.khanabook.saas.payment.controller;

import com.khanabook.saas.payment.dto.RestaurantPaymentConfigResponse;
import com.khanabook.saas.payment.dto.SaveRestaurantPaymentConfigRequest;
import com.khanabook.saas.payment.service.RestaurantPaymentConfigService;
import com.khanabook.saas.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restaurants/payment-config/easebuzz")
@RequiredArgsConstructor
public class RestaurantPaymentConfigController {

    private static final Logger log = LoggerFactory.getLogger(RestaurantPaymentConfigController.class);

    private final RestaurantPaymentConfigService service;

    @PostMapping
    public ResponseEntity<RestaurantPaymentConfigResponse> save(
            @RequestParam(required = false) Long restaurantId,
            @Valid @RequestBody SaveRestaurantPaymentConfigRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.save(resolveTargetTenant(restaurantId, httpRequest), request));
    }

    @GetMapping
    public ResponseEntity<RestaurantPaymentConfigResponse> get(
            @RequestParam(required = false) Long restaurantId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.get(resolveTargetTenant(restaurantId, httpRequest)));
    }

    @PatchMapping("/toggle")
    public ResponseEntity<RestaurantPaymentConfigResponse> toggle(
            @RequestParam boolean enabled,
            @RequestParam(required = false) Long restaurantId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.toggleActive(resolveTargetTenant(restaurantId, httpRequest), enabled));
    }

    private Long resolveTargetTenant(Long overrideId, HttpServletRequest request) {
        Long tenantId = TenantContext.getCurrentTenant();
        String role = TenantContext.getCurrentRole();
        if ("KBOOK_ADMIN".equals(role) && overrideId != null) {
            String admin = SecurityContextHolder.getContext().getAuthentication().getName();
            log.warn("ADMIN_PAYMENT_CONFIG_OVERRIDE admin={} impersonating tenantId={} ip={}",
                    admin, overrideId, request.getRemoteAddr());
            tenantId = overrideId;
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
