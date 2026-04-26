package com.khanabook.saas.storefront.controller;

import com.khanabook.saas.storefront.dto.CreateCustomerOrderRequest;
import com.khanabook.saas.storefront.dto.CreateCustomerOrderResponse;
import com.khanabook.saas.storefront.dto.CustomerOrderStatusResponse;
import com.khanabook.saas.storefront.service.StorefrontService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicOrderController {

    private final StorefrontService storefrontService;

    @PostMapping("/stores/{restaurantId}/orders")
    public ResponseEntity<CreateCustomerOrderResponse> createOrder(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateCustomerOrderRequest request
    ) {
        return ResponseEntity.ok(storefrontService.createCustomerOrder(restaurantId, request));
    }

    @GetMapping("/orders/{trackingToken}")
    public ResponseEntity<CustomerOrderStatusResponse> getOrderStatus(@PathVariable String trackingToken) {
        return ResponseEntity.ok(storefrontService.getOrderStatus(trackingToken));
    }
}
