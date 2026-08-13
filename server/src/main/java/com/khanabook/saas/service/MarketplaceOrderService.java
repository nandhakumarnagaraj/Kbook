package com.khanabook.saas.service;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketplaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceOrderService.class);

    private static final List<String> PENDING_STATUSES = List.of("pending", "accepted", "preparing");
    private static final List<String> ALL_ACTIONABLE = List.of("pending", "accepted", "preparing", "ready", "rejected", "completed");

    private final MarketplaceOrderRepository orderRepo;
    private final PushNotificationService pushNotificationService;

    @Transactional(readOnly = true)
    public List<MarketplaceOrder> getOrders(Long restaurantId) {
        return orderRepo.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceOrder> getPendingOrders(Long restaurantId) {
        return orderRepo.findByRestaurantIdAndOrderStatusInOrderByCreatedAtDesc(restaurantId, PENDING_STATUSES);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getOrderCounts(Long restaurantId) {
        return Map.of(
            "pending",   count(restaurantId, "pending"),
            "accepted",  count(restaurantId, "accepted"),
            "ready",     count(restaurantId, "ready"),
            "completed", count(restaurantId, "completed"),
            "rejected",  count(restaurantId, "rejected")
        );
    }

    private long count(Long restaurantId, String status) {
        return orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, status);
    }

    @Transactional
    public MarketplaceOrder createOrder(MarketplaceOrder order) {
        return orderRepo.findByPlatformAndPlatformOrderId(order.getPlatform(), order.getPlatformOrderId())
                .map(existing -> {
                    log.info("Duplicate marketplace webhook ignored: platform={} platformOrderId={}",
                            order.getPlatform(), order.getPlatformOrderId());
                    return existing;
                })
                .orElseGet(() -> {
                    MarketplaceOrder saved = orderRepo.save(order);
                    log.info("New marketplace order {} saved for restaurantId={} platform={}",
                            saved.getId(), saved.getRestaurantId(), saved.getPlatform());
                    notifyNewOrder(saved);
                    return saved;
                });
    }

    @Transactional
    public MarketplaceOrder acceptOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"pending".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order cannot be accepted. Current status: " + order.getOrderStatus());
        }
        long now = System.currentTimeMillis();
        order.setOrderStatus("accepted");
        order.setAcceptedAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        log.info("Marketplace order {} accepted restaurantId={}", orderId, restaurantId);
        return order;
    }

    @Transactional
    public MarketplaceOrder rejectOrder(Long orderId, Long restaurantId, String reason) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"pending".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order cannot be rejected. Current status: " + order.getOrderStatus());
        }
        long now = System.currentTimeMillis();
        order.setOrderStatus("rejected");
        order.setRejectedAt(now);
        order.setRejectedReason(reason);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        log.info("Marketplace order {} rejected restaurantId={} reason={}", orderId, restaurantId, reason);
        return order;
    }

    @Transactional
    public MarketplaceOrder markReady(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"accepted".equals(order.getOrderStatus()) && !"preparing".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order cannot be marked ready. Current status: " + order.getOrderStatus());
        }
        long now = System.currentTimeMillis();
        order.setOrderStatus("ready");
        order.setReadyAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        log.info("Marketplace order {} marked ready restaurantId={}", orderId, restaurantId);
        return order;
    }

    @Transactional
    public MarketplaceOrder completeOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        long now = System.currentTimeMillis();
        order.setOrderStatus("completed");
        order.setCompletedAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        log.info("Marketplace order {} completed restaurantId={}", orderId, restaurantId);
        return order;
    }

    private MarketplaceOrder getOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Marketplace order not found: " + orderId));
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new IllegalStateException("Order does not belong to this restaurant");
        }
        return order;
    }

    private void notifyNewOrder(MarketplaceOrder order) {
        try {
            String platform = order.getPlatform() != null
                    ? capitalize(order.getPlatform()) : "Marketplace";
            String customer = order.getCustomerName() != null ? order.getCustomerName() : "Customer";
            String amount = order.getTotalAmount() != null ? "₹" + order.getTotalAmount() : "";
            pushNotificationService.pushToRestaurant(
                    order.getRestaurantId(),
                    "New " + platform + " Order!",
                    customer + " ordered " + amount + " — tap to accept",
                    "marketplace_order",
                    order.getId() != null ? order.getId().toString() : null,
                    "marketplace_order",
                    order.getTotalAmount()
            );
        } catch (Exception e) {
            log.warn("Failed to push new marketplace order notification: {}", e.getMessage());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
