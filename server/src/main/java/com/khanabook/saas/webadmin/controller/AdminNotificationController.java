package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final PushNotificationService pushNotificationService;

    /**
     * Send a custom push notification to all active devices of a specific restaurant/shop.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendCustomNotification(@RequestBody Map<String, Object> payload) {
        Object ridObj = payload.get("restaurantId");
        if (ridObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "restaurantId is required"));
        }
        Long restaurantId = Long.valueOf(ridObj.toString());

        String title = (String) payload.getOrDefault("title", "KhanaBook Notification");
        String message = (String) payload.getOrDefault("message", "");
        String type = (String) payload.getOrDefault("type", "custom");
        String referenceId = (String) payload.get("referenceId");
        String referenceType = (String) payload.get("referenceType");

        BigDecimal amount = BigDecimal.ZERO;
        if (payload.containsKey("amount")) {
            try {
                amount = new BigDecimal(payload.get("amount").toString());
            } catch (Exception e) {
                // use default zero
            }
        }

        pushNotificationService.pushToRestaurant(restaurantId, title, message, type, referenceId, referenceType, amount);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Custom notification triggered"));
    }
}
