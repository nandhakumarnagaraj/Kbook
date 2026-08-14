package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.FssaiTrackerService;
import com.khanabook.saas.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Test/debug endpoints for push notifications and FSSAI.
 * Only registered as a bean in non-prod profiles — completely absent from production builds.
 * Additionally gated behind KBOOK_ADMIN as defence-in-depth.
 */
@RestController
@RequestMapping("/notifications/test")
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class NotificationTestController {

    private final PushNotificationService pushNotificationService;
    private final FssaiTrackerService fssaiTrackerService;

    /** Send a welcome/greeting push to all active devices for the current restaurant */
    @PostMapping
    @RequireRole(UserRole.KBOOK_ADMIN)
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

    /** Trigger the FSSAI tracking job manually */
    @PostMapping("/fssai")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Map<String, Object>> triggerFssaiTrack() {
        fssaiTrackerService.trackFssaiLicenses();
        return ResponseEntity.ok(Map.of("status", "success", "message", "FSSAI tracking job triggered manually"));
    }

    /** Send a direct push notification to a specific FCM token */
    @PostMapping("/direct-push")
    @RequireRole(UserRole.KBOOK_ADMIN)
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> sendDirectPush(@RequestBody Map<String, Object> payload) {
        String token = (String) payload.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "token is required"));
        }
        String title = (String) payload.getOrDefault("title", "FSSAI License Expiring Soon");
        String body = (String) payload.getOrDefault("body", "Your FSSAI license for Demo Restaurant expires in 30 days. Renew now to avoid penalties.");
        Map<String, String> data = (Map<String, String>) payload.get("data");
        if (data == null) {
            data = Map.of(
                "type", "FSSAI_ALERT",
                "restaurantId", "REST001",
                "daysLeft", "30"
            );
        }

        try {
            String messageId = pushNotificationService.sendDirectPush(token, title, body, data);
            if (messageId == null) {
                return ResponseEntity.ok(Map.of("status", "skipped", "reason", "Firebase not configured"));
            }
            return ResponseEntity.ok(Map.of("status", "success", "messageId", messageId));
        } catch (Exception e) {
            log.error("Failed to send direct push notification", e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
