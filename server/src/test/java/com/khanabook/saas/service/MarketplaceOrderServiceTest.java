package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class MarketplaceOrderServiceTest extends BaseIntegrationTest {

    @Autowired private MarketplaceOrderService orderService;
    @Autowired private MarketplaceOrderRepository orderRepo;

    private Long testRestaurantId;

    @BeforeEach
    void setup() {
        testRestaurantId = 700L + System.currentTimeMillis() % 100;
        persistUser("admin" + System.currentTimeMillis() + "@test.com", testRestaurantId, UserRole.KBOOK_ADMIN);
    }

    @Test
    void getOrders_shouldReturnOrdersDescending() {
        long now = System.currentTimeMillis();
        MarketplaceOrder o1 = createOrder("ZOMATO", "Z_001", "pending", now - 2000);
        MarketplaceOrder o2 = createOrder("SWIGGY", "S_001", "accepted", now - 1000);
        MarketplaceOrder o3 = createOrder("ZOMATO", "Z_002", "completed", now);

        List<MarketplaceOrder> orders = orderService.getOrders(testRestaurantId);

        assertEquals(3, orders.size());
        assertTrue(orders.get(0).getCreatedAt() >= orders.get(1).getCreatedAt());
    }

    @Test
    void getPendingOrders_shouldReturnOnlyPendingAcceptedPreparing() {
        createOrder("SWIGGY", "S_P1", "pending", System.currentTimeMillis());
        createOrder("SWIGGY", "S_P2", "accepted", System.currentTimeMillis());
        createOrder("ZOMATO", "Z_R", "ready", System.currentTimeMillis());
        createOrder("ZOMATO", "Z_C", "completed", System.currentTimeMillis());

        List<MarketplaceOrder> pending = orderService.getPendingOrders(testRestaurantId);

        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(o ->
            List.of("pending", "accepted", "preparing").contains(o.getOrderStatus())
        ));
    }

    @Test
    void getOrderCounts_shouldReturnCorrectCounts() {
        createOrder("SWIGGY", "S_1", "pending", System.currentTimeMillis());
        createOrder("SWIGGY", "S_2", "pending", System.currentTimeMillis());
        createOrder("ZOMATO", "Z_1", "accepted", System.currentTimeMillis());
        createOrder("ZOMATO", "Z_2", "ready", System.currentTimeMillis());
        createOrder("SWIGGY", "S_3", "rejected", System.currentTimeMillis());

        Map<String, Long> counts = orderService.getOrderCounts(testRestaurantId);

        assertEquals(2L, counts.get("pending"));
        assertEquals(1L, counts.get("accepted"));
        assertEquals(1L, counts.get("ready"));
        assertEquals(1L, counts.get("rejected"));
    }

    @Test
    void acceptOrder_shouldUpdateStatus() {
        MarketplaceOrder order = createOrder("SWIGGY", "S_ACCEPT", "pending", System.currentTimeMillis());

        MarketplaceOrder accepted = orderService.acceptOrder(order.getId(), testRestaurantId);

        assertEquals("accepted", accepted.getOrderStatus());
        assertNotNull(accepted.getAcceptedAt());
    }

    @Test
    void acceptOrder_nonPending_shouldThrow() {
        MarketplaceOrder order = createOrder("ZOMATO", "Z_ACCEPT", "completed", System.currentTimeMillis());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> orderService.acceptOrder(order.getId(), testRestaurantId));
        assertTrue(ex.getMessage().contains("cannot be accepted"));
    }

    @Test
    void rejectOrder_shouldUpdateStatus() {
        MarketplaceOrder order = createOrder("ZOMATO", "Z_REJECT", "pending", System.currentTimeMillis());

        MarketplaceOrder rejected = orderService.rejectOrder(order.getId(), testRestaurantId, "Out of stock");

        assertEquals("rejected", rejected.getOrderStatus());
        assertEquals("Out of stock", rejected.getRejectedReason());
        assertNotNull(rejected.getRejectedAt());
    }

    @Test
    void rejectOrder_nonPending_shouldThrow() {
        MarketplaceOrder order = createOrder("SWIGGY", "S_REJECT", "accepted", System.currentTimeMillis());

        assertThrows(RuntimeException.class,
            () -> orderService.rejectOrder(order.getId(), testRestaurantId, "No reason"));
    }

    @Test
    void markReady_shouldUpdateStatus() {
        MarketplaceOrder order = createOrder("SWIGGY", "S_READY", "accepted", System.currentTimeMillis());

        MarketplaceOrder ready = orderService.markReady(order.getId(), testRestaurantId);

        assertEquals("ready", ready.getOrderStatus());
        assertNotNull(ready.getReadyAt());
    }

    @Test
    void markReady_fromPreparing_shouldUpdateStatus() {
        MarketplaceOrder order = createOrder("ZOMATO", "Z_READY", "preparing", System.currentTimeMillis());

        MarketplaceOrder ready = orderService.markReady(order.getId(), testRestaurantId);

        assertEquals("ready", ready.getOrderStatus());
    }

    @Test
    void markReady_nonAcceptedOrPreparing_shouldThrow() {
        MarketplaceOrder order = createOrder("ZOMATO", "Z_READY2", "pending", System.currentTimeMillis());

        assertThrows(RuntimeException.class,
            () -> orderService.markReady(order.getId(), testRestaurantId));
    }

    @Test
    void completeOrder_shouldUpdateStatus() {
        MarketplaceOrder order = createOrder("SWIGGY", "S_COMP", "ready", System.currentTimeMillis());

        MarketplaceOrder completed = orderService.completeOrder(order.getId(), testRestaurantId);

        assertEquals("completed", completed.getOrderStatus());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void completeOrder_fromAnyStatus_shouldWork() {
        MarketplaceOrder order = createOrder("ZOMATO", "Z_COMP", "pending", System.currentTimeMillis());

        MarketplaceOrder completed = orderService.completeOrder(order.getId(), testRestaurantId);

        assertEquals("completed", completed.getOrderStatus());
    }

    @Test
    void operation_wrongRestaurant_shouldThrow() {
        Long wrongRestaurantId = 999999L;
        MarketplaceOrder order = createOrder("SWIGGY", "S_WRONG", "pending", System.currentTimeMillis());

        assertThrows(RuntimeException.class,
            () -> orderService.acceptOrder(order.getId(), wrongRestaurantId));
    }

    private MarketplaceOrder createOrder(String platform, String platformOrderId, String orderStatus, long now) {
        MarketplaceOrder order = new MarketplaceOrder();
        order.setRestaurantId(testRestaurantId);
        order.setPlatform(platform);
        order.setPlatformOrderId(platformOrderId);
        order.setOrderStatus(orderStatus);
        order.setTotalAmount(BigDecimal.valueOf(100));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return orderRepo.save(order);
    }
}
