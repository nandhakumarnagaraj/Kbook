package com.khanabook.saas.payment.controller;

import com.khanabook.saas.payment.dto.CreateEasebuzzOrderRequest;
import com.khanabook.saas.payment.dto.CreateEasebuzzOrderResponse;
import com.khanabook.saas.payment.dto.EasebuzzPaymentStatusResponse;
import com.khanabook.saas.payment.service.EasebuzzPaymentService;
import com.khanabook.saas.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class EasebuzzPaymentController {

    private final EasebuzzPaymentService service;

    @PostMapping("/create-order")
    public ResponseEntity<CreateEasebuzzOrderResponse> createOrder(
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody CreateEasebuzzOrderRequest request
    ) {
        return ResponseEntity.ok(service.createOrder(
                TenantContext.getCurrentTenant(),
                deviceId,
                null,
                request
        ));
    }

    @GetMapping("/status/{billId}")
    public ResponseEntity<EasebuzzPaymentStatusResponse> status(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable Long billId
    ) {
        return ResponseEntity.ok(service.getStatus(TenantContext.getCurrentTenant(), deviceId, billId));
    }
}
