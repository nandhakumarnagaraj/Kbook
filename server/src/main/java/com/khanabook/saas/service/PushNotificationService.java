package com.khanabook.saas.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.khanabook.saas.entity.DeviceToken;
import com.khanabook.saas.entity.NotificationEvent;
import com.khanabook.saas.repository.DeviceTokenRepository;
import com.khanabook.saas.repository.NotificationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final DeviceTokenRepository deviceTokenRepo;
    private final NotificationEventRepository notificationEventRepo;
    private final FirebaseApp firebaseApp;

    @Autowired
    public PushNotificationService(DeviceTokenRepository deviceTokenRepo,
                                   NotificationEventRepository notificationEventRepo,
                                   @Autowired(required = false) FirebaseApp firebaseApp) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.notificationEventRepo = notificationEventRepo;
        this.firebaseApp = firebaseApp;
        if (firebaseApp == null) {
            log.warn("FirebaseApp not available. Push notifications will be DISABLED.");
        }
    }

    /**
     * Register or update a device token for push notifications.
     */
    @Transactional
    public DeviceToken registerToken(Long restaurantId, String token, String platform, String deviceId) {
        deviceTokenRepo.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
            .ifPresent(existing -> {
                existing.setActive(false);
                deviceTokenRepo.save(existing);
            });

        DeviceToken dt = deviceTokenRepo.findByToken(token)
            .orElseGet(DeviceToken::new);

        dt.setRestaurantId(restaurantId);
        dt.setToken(token);
        dt.setPlatform(platform != null ? platform : "android");
        dt.setDeviceId(deviceId);
        dt.setActive(true);
        long now = System.currentTimeMillis();
        if (dt.getCreatedAt() == null) dt.setCreatedAt(now);
        dt.setUpdatedAt(now);
        return deviceTokenRepo.save(dt);
    }

    /**
     * Unregister a device token (logout / disable).
     */
    @Transactional
    public void unregisterToken(Long restaurantId, String deviceId) {
        deviceTokenRepo.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
            .ifPresent(token -> {
                token.setActive(false);
                token.setUpdatedAt(System.currentTimeMillis());
                deviceTokenRepo.save(token);
            });
    }

    /**
     * Send push notification to all active devices for a restaurant.
     */
    public void pushToRestaurant(Long restaurantId, String title, String message,
                                  String notificationType, String referenceId,
                                  String referenceType, BigDecimal amount) {
        if (firebaseApp == null) {
            log.debug("Firebase not configured, skipping push to restaurantId={}", restaurantId);
            return;
        }
        List<DeviceToken> tokens = deviceTokenRepo.findByRestaurantIdAndActiveTrue(restaurantId);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens for restaurantId={}", restaurantId);
            return;
        }

        // Save notification event
        NotificationEvent event = saveNotificationEvent(restaurantId, title, message,
            notificationType, referenceId, referenceType, amount);

        // Build FCM message
        Notification notification = Notification.builder()
            .setTitle(title)
            .setBody(message)
            .build();

        Map<String, String> data = Map.of(
            "type", notificationType != null ? notificationType : "",
            "referenceId", referenceId != null ? referenceId : "",
            "referenceType", referenceType != null ? referenceType : "",
            "notificationId", event.getId().toString(),
            "amount", amount != null ? amount.toPlainString() : ""
        );

        MulticastMessage multicast = MulticastMessage.builder()
            .setNotification(notification)
            .putAllData(data)
            .addAllTokens(tokens.stream().map(DeviceToken::getToken).toList())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                    .setChannelId(resolveChannelId(notificationType))
                    .setSound("default")
                    .setPriority(AndroidNotification.Priority.HIGH)
                    .build())
                .build())
            .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(multicast);
            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();
            log.info("Push sent to restaurantId={} success={} failure={} type={}",
                restaurantId, successCount, failureCount, notificationType);

            // Mark event as pushed
            event.setIsPushed(true);
            notificationEventRepo.save(event);

            if (failureCount > 0) {
                for (var sendResponse : response.getResponses()) {
                    if (!sendResponse.isSuccessful()) {
                        String errorMsg = sendResponse.getException() != null ?
                            sendResponse.getException().getMessage() : "unknown";
                        log.warn("Push failed for token: {}", errorMsg);
                        // Optionally deactivate invalid tokens
                        if (errorMsg.contains("UNREGISTERED") || errorMsg.contains("InvalidRegistration")) {
                            int idx = response.getResponses().indexOf(sendResponse);
                            if (idx < tokens.size()) {
                                DeviceToken dt = tokens.get(idx);
                                dt.setActive(false);
                                dt.setUpdatedAt(System.currentTimeMillis());
                                deviceTokenRepo.save(dt);
                            }
                        }
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push to restaurantId={}: {}", restaurantId, e.getMessage());
        }
    }

    private NotificationEvent saveNotificationEvent(Long restaurantId, String title, String message,
                                                     String notificationType, String referenceId,
                                                     String referenceType, BigDecimal amount) {
        NotificationEvent event = new NotificationEvent();
        event.setRestaurantId(restaurantId);
        event.setNotificationType(notificationType);
        event.setTitle(title);
        event.setMessage(message);
        event.setReferenceId(referenceId);
        event.setReferenceType(referenceType);
        event.setAmount(amount);
        event.setIsRead(false);
        event.setIsPushed(false);
        event.setCreatedAt(System.currentTimeMillis());
        return notificationEventRepo.save(event);
    }

    /** Map notification type to the correct Android channel ID. */
    private String resolveChannelId(String type) {
        if (type == null) return "khanabook_system";
        return switch (type) {
            case "payment_received"  -> "khanabook_payment";
            case "refund"            -> "khanabook_refund";
            case "kyc"               -> "khanabook_kyc";
            case "settlement"        -> "khanabook_settlement";
            case "marketplace_order" -> "khanabook_payment";
            default                  -> "khanabook_system";
        };
    }

    public List<NotificationEvent> getNotifications(Long restaurantId, int limit) {
        return notificationEventRepo.findByRestaurantIdOrderByCreatedAtDesc(restaurantId,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public long getUnreadCount(Long restaurantId) {
        return notificationEventRepo.countByRestaurantIdAndIsReadFalse(restaurantId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationEventRepo.markAsRead(notificationId, System.currentTimeMillis());
    }

    @Transactional
    public void markAllAsRead(Long restaurantId) {
        notificationEventRepo.markAllAsRead(restaurantId, System.currentTimeMillis());
    }
}
