package com.khanabook.saas.integration;

import com.khanabook.saas.billing.domain.MarketplaceOrder;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.integration.dto.MarketplaceConfigRequest;
import com.khanabook.saas.integration.dto.MarketplaceConfigResponse;
import com.khanabook.saas.integration.zomato.ZomatoIntegrationService;
import com.khanabook.saas.integration.swiggy.SwiggyIntegrationService;
import com.khanabook.saas.billing.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.common.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final ZomatoIntegrationService zomatoIntegrationService;
    private final SwiggyIntegrationService swiggyIntegrationService;
    private final MarketplaceConfigService marketplaceConfigService;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        try {
            entityManager.createNativeQuery("SELECT 1 FROM information_schema.tables WHERE table_name = 'marketplace_orders'").getSingleResult();
            status.put("marketplace_orders_table", "exists");
        } catch (Exception e) {
            status.put("marketplace_orders_table", "missing: " + e.getMessage());
        }
        try {
            entityManager.createNativeQuery("SELECT 1 FROM information_schema.tables WHERE table_name = 'marketplace_order_items'").getSingleResult();
            status.put("marketplace_order_items_table", "exists");
        } catch (Exception e) {
            status.put("marketplace_order_items_table", "missing: " + e.getMessage());
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/zomato/config")
    public ResponseEntity<?> saveZomatoConfig(
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String webhookSecret,
            @RequestParam(required = false) String outletId,
            @RequestParam(required = false) Boolean enabled
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant profile not found"));
        if (outletId != null) profile.setZomatoOutletId(outletId);
        restaurantProfileRepository.save(profile);
        MarketplaceConfigResponse response = marketplaceConfigService.save(restaurantId,
                new MarketplaceConfigRequest(apiKey, webhookSecret, enabled, null, null, null));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/swiggy/config")
    public ResponseEntity<?> saveSwiggyConfig(
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String webhookSecret,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) Boolean enabled
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant profile not found"));
        if (storeId != null) profile.setSwiggyStoreId(storeId);
        restaurantProfileRepository.save(profile);
        MarketplaceConfigResponse response = marketplaceConfigService.save(restaurantId,
                new MarketplaceConfigRequest(null, null, null, apiKey, webhookSecret, enabled));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getMarketplaceOrders(
            @RequestParam(required = false, defaultValue = "ALL") String platform
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        List<MarketplaceOrder> orders;
        if ("ZOMATO".equalsIgnoreCase(platform)) {
            orders = marketplaceOrderRepository.findByRestaurantIdAndPlatformOrderByCreatedAtDesc(restaurantId, "ZOMATO");
        } else if ("SWIGGY".equalsIgnoreCase(platform)) {
            orders = marketplaceOrderRepository.findByRestaurantIdAndPlatformOrderByCreatedAtDesc(restaurantId, "SWIGGY");
        } else {
            orders = marketplaceOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        }
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/zomato/orders/{platformOrderId}")
    public ResponseEntity<?> getZomatoOrderDetails(
            @PathVariable String platformOrderId
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Restaurant profile not found"));

            String apiKey = marketplaceConfigService.decryptZomatoApiKey(profile);
            if (!Boolean.TRUE.equals(profile.getZomatoEnabled()) || apiKey == null) {
                return ResponseEntity.status(400).body(Map.of("error", "Zomato integration not configured"));
            }

            var orderDetails = zomatoIntegrationService.processWebhook(restaurantId,
                    zomatoIntegrationService.toString(zomatoIntegrationService.fetchOrderDetails(apiKey, platformOrderId)));

            return ResponseEntity.ok(orderDetails);

        } catch (Exception e) {
            log.error("Error fetching Zomato order {}: {}", platformOrderId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    @PostMapping("/orders/{marketplaceOrderId}/convert-to-bill")
    public ResponseEntity<?> convertToBill(
            @PathVariable Long marketplaceOrderId
    ) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            MarketplaceOrder order = marketplaceOrderRepository.findById(marketplaceOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("Marketplace order not found"));

            if (!order.getRestaurantId().equals(restaurantId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            if ("ZOMATO".equals(order.getPlatform())) {
                var bill = zomatoIntegrationService.convertToBill(restaurantId, marketplaceOrderId);
                return ResponseEntity.ok(Map.of("success", true, "billId", bill.getId()));
            } else if ("SWIGGY".equals(order.getPlatform())) {
                var bill = swiggyIntegrationService.convertToBill(restaurantId, marketplaceOrderId);
                return ResponseEntity.ok(Map.of("success", true, "billId", bill.getId()));
            } else {
                return ResponseEntity.status(400).body(Map.of("error", "Unknown platform"));
            }
        } catch (Exception e) {
            log.error("Error converting marketplace order to bill: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<MarketplaceConfigResponse> getMarketplaceConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        return ResponseEntity.ok(marketplaceConfigService.get(restaurantId));
    }

    @PostMapping("/config")
    public ResponseEntity<MarketplaceConfigResponse> saveMarketplaceConfig(@RequestBody MarketplaceConfigRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        return ResponseEntity.ok(marketplaceConfigService.save(restaurantId, request));
    }
}
