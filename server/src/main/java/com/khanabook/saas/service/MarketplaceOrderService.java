package com.khanabook.saas.service;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketplaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceOrderService.class);
    private final MarketplaceOrderRepository orderRepo;

    @Transactional(readOnly = true)
    public List<MarketplaceOrder> getOrders(Long restaurantId) {
        return orderRepo.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceOrder> getPendingOrders(Long restaurantId) {
        return orderRepo.findByRestaurantIdAndOrderStatusInOrderByCreatedAtDesc(
                restaurantId, List.of("pending", "accepted", "preparing"));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orderCounts", key = "#restaurantId")
    public Map<String, Long> getOrderCounts(Long restaurantId) {
        return Map.of(
            "pending", orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, "pending"),
            "accepted", orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, "accepted"),
            "ready", orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, "ready"),
            "completed", orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, "completed"),
            "rejected", orderRepo.countByRestaurantIdAndOrderStatus(restaurantId, "rejected")
        );
    }

    @Transactional
    @CacheEvict(value = "orderCounts", key = "#restaurantId")
    public MarketplaceOrder acceptOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"pending".equals(order.getOrderStatus())) {
            throw new RuntimeException("Order cannot be accepted. Current status: " + order.getOrderStatus());
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
    @CacheEvict(value = "orderCounts", key = "#restaurantId")
    public MarketplaceOrder rejectOrder(Long orderId, Long restaurantId, String reason) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"pending".equals(order.getOrderStatus())) {
            throw new RuntimeException("Order cannot be rejected. Current status: " + order.getOrderStatus());
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
    @CacheEvict(value = "orderCounts", key = "#restaurantId")
    public MarketplaceOrder markReady(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        if (!"accepted".equals(order.getOrderStatus()) && !"preparing".equals(order.getOrderStatus())) {
            throw new RuntimeException("Order cannot be marked ready. Current status: " + order.getOrderStatus());
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
    @CacheEvict(value = "orderCounts", key = "#restaurantId")
    public MarketplaceOrder completeOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = getOrder(orderId, restaurantId);
        long now = System.currentTimeMillis();
        order.setOrderStatus("completed");
        order.setCompletedAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        return order;
    }

    private MarketplaceOrder getOrder(Long orderId, Long restaurantId) {
        MarketplaceOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Marketplace order not found: " + orderId));
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new RuntimeException("Order does not belong to this restaurant");
        }
        return order;
    }
}
