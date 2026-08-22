package com.khanabook.saas.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.khanabook.saas.entity.DeviceToken;
import com.khanabook.saas.entity.NotificationEvent;
import com.khanabook.saas.repository.DeviceTokenRepository;
import com.khanabook.saas.repository.NotificationEventRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
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
    private final RestaurantProfileRepository restaurantProfileRepo;
    private final FirebaseApp firebaseApp;

    @Autowired
    public PushNotificationService(DeviceTokenRepository deviceTokenRepo,
                                   NotificationEventRepository notificationEventRepo,
                                   RestaurantProfileRepository restaurantProfileRepo,
                                   @Autowired(required = false) FirebaseApp firebaseApp) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.notificationEventRepo = notificationEventRepo;
        this.restaurantProfileRepo = restaurantProfileRepo;
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

        // Look up within this restaurant only; never rewrite another tenant's row
        DeviceToken dt = deviceTokenRepo.findByRestaurantIdAndToken(restaurantId, token)
            .orElseGet(DeviceToken::new);

        // Device switched shops: deactivate the stale row under the old restaurant
        deviceTokenRepo.findByToken(token)
            .filter(stale -> !stale.getRestaurantId().equals(restaurantId) && Boolean.TRUE.equals(stale.getActive()))
            .ifPresent(stale -> {
                stale.setActive(false);
                stale.setUpdatedAt(System.currentTimeMillis());
                deviceTokenRepo.save(stale);
                log.info("Device token reassigned from restaurantId={} to restaurantId={}",
                    stale.getRestaurantId(), restaurantId);
            });

        dt.setRestaurantId(restaurantId);
        dt.setToken(token);
        dt.setPlatform(platform != null ? platform : "android");
        dt.setDeviceId(deviceId);
        Long currentUserId = com.khanabook.saas.security.TenantContext.getCurrentUserId();
        if (currentUserId != null) {
            dt.setUserId(currentUserId);
        }
        dt.setActive(true);
        long now = System.currentTimeMillis();
        if (dt.getCreatedAt() == null) dt.setCreatedAt(now);
        dt.setUpdatedAt(now);
        DeviceToken saved = deviceTokenRepo.save(dt);
        try {
            restaurantProfileRepo.findByRestaurantId(restaurantId).ifPresent(profile -> {
                String shopName = profile.getShopName() != null ? profile.getShopName() : "Restaurant";
                String customWelcome = profile.getCustomWelcomeMessage();
                String body;
                if (customWelcome != null && !customWelcome.isBlank()) {
                    body = customWelcome.replace("{shopName}", shopName);
                } else {
                    body = "Welcome back to " + shopName + ". Push notifications are active.";
                }
                this.pushToRestaurant(
                    restaurantId,
                    "Welcome back!",
                    body,
                    "system",
                    null,
                    null,
                    BigDecimal.ZERO
                );
            });
        } catch (Exception e) {
            log.warn("Failed to push welcome notification: {}", e.getMessage());
        }
        return saved;
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

        // Build Notification block for automatic background OS display
        Notification fcmNotification = Notification.builder()
            .setTitle(title)
            .setBody(message)
            .build();

        Map<String, String> data = Map.of(
            "title", title != null ? title : "",
            "message", message != null ? message : "",
            "type", notificationType != null ? notificationType : "",
            "referenceId", referenceId != null ? referenceId : "",
            "referenceType", referenceType != null ? referenceType : "",
            "notificationId", event.getId().toString(),
            "amount", amount != null ? amount.toPlainString() : ""
        );

        // Android-specific configuration (high priority, channel ID, sound)
        AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.builder()
            .setChannelId(resolveChannelId(notificationType))
            .setSound("default")
            .setPriority(AndroidNotification.Priority.HIGH);

        MulticastMessage multicast = MulticastMessage.builder()
            .setNotification(fcmNotification) // Combined payload!
            .putAllData(data)
            .addAllTokens(tokens.stream().map(DeviceToken::getToken).toList())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotificationBuilder.build())
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

    /**
     * Send push notification to specific users' devices within a restaurant.
     * Falls back gracefully when no matching devices are registered.
     */
    public void pushToUsers(Long restaurantId, List<Long> userIds, String title, String message,
                            String notificationType, String referenceId,
                            String referenceType, BigDecimal amount) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        if (firebaseApp == null) {
            log.debug("Firebase not configured, skipping push to users {} for restaurantId={}", userIds, restaurantId);
            return;
        }
        List<DeviceToken> tokens = deviceTokenRepo.findByRestaurantIdAndUserIdInAndActiveTrue(restaurantId, userIds);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens for users {} restaurantId={}", userIds, restaurantId);
            return;
        }

        NotificationEvent event = saveNotificationEvent(restaurantId, title, message,
            notificationType, referenceId, referenceType, amount);

        int successCount = 0;
        for (DeviceToken dt : tokens) {
            Map<String, String> data = Map.of(
                "title", title != null ? title : "",
                "message", message != null ? message : "",
                "type", notificationType != null ? notificationType : "",
                "referenceId", referenceId != null ? referenceId : "",
                "referenceType", referenceType != null ? referenceType : "",
                "notificationId", event.getId().toString(),
                "amount", amount != null ? amount.toPlainString() : ""
            );
            try {
                sendDirectPush(dt.getToken(), title, message, data);
                successCount++;
            } catch (FirebaseMessagingException e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "unknown";
                log.warn("Targeted push failed for token: {}", errorMsg);
                if (errorMsg.contains("UNREGISTERED") || errorMsg.contains("InvalidRegistration")) {
                    dt.setActive(false);
                    dt.setUpdatedAt(System.currentTimeMillis());
                    deviceTokenRepo.save(dt);
                }
            } catch (Exception e) {
                log.warn("Targeted push failed unexpectedly: {}", e.getMessage());
            }
        }

        log.info("Targeted push sent to users={} restaurantId={} success={}/{} type={}",
            userIds, restaurantId, successCount, tokens.size(), notificationType);

        if (successCount > 0) {
            event.setIsPushed(true);
            notificationEventRepo.save(event);
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
		if (type == null) return "khanabook_system_v2";
		return switch (type) {
			case "payment_received", "qr_order" -> "khanabook_payment_v2";
			case "refund"            -> "khanabook_refund_v2";
			case "kyc"               -> "khanabook_kyc_v2";
			case "settlement"        -> "khanabook_settlement_v2";
			case "marketplace_order" -> "khanabook_payment_v2";
			case "inventory_low"     -> "khanabook_inventory_v2";
			case "permission_request", "permission_approved", "permission_rejected"
			                         -> "khanabook_permissions_v2";
			default                  -> "khanabook_system_v2";
		};
	}

	/**
	 * Retention: notification_events older than 90 days are purged daily.
	 * The in-app notification center is a recent-history feed, not an archive.
	 */
	@org.springframework.scheduling.annotation.Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
	@Transactional
	public void purgeOldNotificationEvents() {
		long cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L;
		int deleted = notificationEventRepo.deleteByCreatedAtBefore(cutoff);
		if (deleted > 0) {
			log.info("Purged {} notification events older than {} days", deleted, RETENTION_DAYS);
		}
	}

	private static final int RETENTION_DAYS = 90;

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

    public String sendDirectPush(String token, String title, String body, Map<String, String> data) throws FirebaseMessagingException {
        if (firebaseApp == null) {
            log.warn("Firebase not configured, skipping direct push to token={}", token);
            return null;
        }

        java.util.Map<String, String> payloadData = new java.util.HashMap<>();
        if (data != null) {
            payloadData.putAll(data);
        }
        payloadData.put("title", title != null ? title : "");
        payloadData.put("message", body != null ? body : "");

        Notification fcmNotification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        String notificationType = data != null ? data.get("type") : "system";
        String referenceId = data != null ? data.get("referenceId") : null;

        AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.builder()
            .setChannelId(resolveChannelId(notificationType))
            .setSound("default")
            .setPriority(AndroidNotification.Priority.HIGH);

        Message message = Message.builder()
            .setToken(token)
            .setNotification(fcmNotification)
            .putAllData(payloadData)
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotificationBuilder.build())
                .build())
            .build();

        return FirebaseMessaging.getInstance(firebaseApp).send(message);
    }
}
