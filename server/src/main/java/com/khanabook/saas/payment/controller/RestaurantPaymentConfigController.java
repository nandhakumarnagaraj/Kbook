package com.khanabook.saas.payment.controller;

import com.khanabook.saas.payment.dto.RestaurantPaymentConfigResponse;
import com.khanabook.saas.payment.dto.SaveRestaurantPaymentConfigRequest;
import com.khanabook.saas.payment.service.RestaurantPaymentConfigService;
import com.khanabook.saas.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restaurants/payment-config/easebuzz")
@RequiredArgsConstructor
public class RestaurantPaymentConfigController {

    private final RestaurantPaymentConfigService service;

    @PostMapping
    public ResponseEntity<RestaurantPaymentConfigResponse> save(@Valid @RequestBody SaveRestaurantPaymentConfigRequest request) {
        return ResponseEntity.ok(service.save(TenantContext.getCurrentTenant(), request));
    }

    @GetMapping
    public ResponseEntity<RestaurantPaymentConfigResponse> get() {
        return ResponseEntity.ok(service.get(TenantContext.getCurrentTenant()));
    }
}
