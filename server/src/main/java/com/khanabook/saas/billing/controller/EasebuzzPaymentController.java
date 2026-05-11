package com.khanabook.saas.billing.controller;

import com.khanabook.saas.billing.dto.CreateEasebuzzOrderRequest;
import com.khanabook.saas.billing.dto.CreateEasebuzzOrderResponse;
import com.khanabook.saas.billing.dto.EasebuzzPaymentStatusResponse;
import com.khanabook.saas.billing.dto.InitiatePosRefundRequest;
import com.khanabook.saas.billing.service.EasebuzzPaymentService;
import com.khanabook.saas.common.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

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
            @PathVariable Long billId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return ResponseEntity.ok(refresh
                ? service.verifyWithGateway(TenantContext.getCurrentTenant(), deviceId, billId)
                : service.getStatus(TenantContext.getCurrentTenant(), deviceId, billId));
    }

    @PostMapping("/verify/{billId}")
    public ResponseEntity<EasebuzzPaymentStatusResponse> verify(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable Long billId
    ) {
        return ResponseEntity.ok(service.verifyWithGateway(TenantContext.getCurrentTenant(), deviceId, billId));
    }

    @PostMapping("/refund/{billId}")
    public ResponseEntity<Map<String, String>> initiateRefund(
            @PathVariable Long billId,
            @RequestBody InitiatePosRefundRequest request
    ) {
        service.initiateGatewayRefund(
                TenantContext.getCurrentTenant(),
                billId,
                request.refundAmount(),
                request.reason()
        );
        return ResponseEntity.ok(Map.of("status", "refund_initiated"));
    }
}
