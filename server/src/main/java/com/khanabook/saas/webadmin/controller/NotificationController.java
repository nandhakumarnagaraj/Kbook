package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.DeviceToken;
import com.khanabook.saas.entity.NotificationEvent;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final PushNotificationService pushNotificationService;

    /** Test endpoint: send a welcome/greeting push to all active devices */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestNotification(@RequestBody(required = false) Map<String, String> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        String title = data != null ? data.getOrDefault("title", "👋 Welcome to KhanaBook!") : "👋 Welcome to KhanaBook!";
        String message = data != null ? data.getOrDefault("message", "Your push notifications are working perfectly.") : "Your push notifications are working perfectly.";
        pushNotificationService.pushToRestaurant(restaurantId, title, message, "system", null, null, BigDecimal.ZERO);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Test notification sent"));
    }

    /** Register FCM device token for push notifications */
    @PostMapping("/device-token")
    public ResponseEntity<Map<String, Object>> registerDeviceToken(@RequestBody Map<String, String> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        String token = data.get("token");
        String platform = data.getOrDefault("platform", "android");
        String deviceId = data.getOrDefault("deviceId", "");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "token is required"));
        }
        pushNotificationService.registerToken(restaurantId, token, platform, deviceId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    /** Unregister device token */
    @DeleteMapping("/device-token")
    public ResponseEntity<Map<String, Object>> unregisterDeviceToken(@RequestParam String deviceId) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        pushNotificationService.unregisterToken(restaurantId, deviceId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    /** Get recent notifications */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(defaultValue = "50") int limit) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        List<NotificationEvent> notifications = pushNotificationService.getNotifications(restaurantId, limit);
        long unreadCount = pushNotificationService.getUnreadCount(restaurantId);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "notifications", notifications,
            "unreadCount", unreadCount
        ));
    }

    /** Get unread notification count */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        long count = pushNotificationService.getUnreadCount(restaurantId);
        return ResponseEntity.ok(Map.of("status", "success", "unreadCount", count));
    }

    /** Mark single notification as read */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        pushNotificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    /** Mark all notifications as read */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No restaurant context"));
        }
        pushNotificationService.markAllAsRead(restaurantId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
