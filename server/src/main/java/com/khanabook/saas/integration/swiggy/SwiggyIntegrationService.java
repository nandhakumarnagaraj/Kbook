package com.khanabook.saas.integration.swiggy;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.billing.domain.MarketplaceOrder;
import com.khanabook.saas.billing.domain.MarketplaceOrderItem;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.integration.MarketplaceConfigService;
import com.khanabook.saas.billing.repository.BillRepository;
import com.khanabook.saas.billing.repository.MarketplaceOrderItemRepository;
import com.khanabook.saas.billing.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwiggyIntegrationService {

    private final SwiggyApiClient apiClient;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final MarketplaceOrderItemRepository marketplaceOrderItemRepository;
    private final BillRepository billRepository;
    private final MarketplaceConfigService marketplaceConfigService;

    @Transactional
    public MarketplaceOrder processWebhook(Long restaurantId, String payload) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant profile not found"));

        if (!Boolean.TRUE.equals(profile.getSwiggyEnabled()) || marketplaceConfigService.decryptSwiggyApiKey(profile) == null) {
            throw new IllegalStateException("Swiggy integration not configured for this restaurant");
        }

        JsonNode orderData = parsePayload(payload);
        String platformOrderId = extractPlatformOrderId(orderData);

        MarketplaceOrder marketplaceOrder = marketplaceOrderRepository
                .findByPlatformAndPlatformOrderId("SWIGGY", platformOrderId)
                .orElse(null);

        if (marketplaceOrder == null) {
            marketplaceOrder = createMarketplaceOrder(restaurantId, orderData, platformOrderId);
            marketplaceOrderRepository.save(marketplaceOrder);

            List<MarketplaceOrderItem> items = createOrderItems(marketplaceOrder.getId(), orderData);
            marketplaceOrderItemRepository.saveAll(items);
        } else {
            updateMarketplaceOrder(marketplaceOrder, orderData);
        }

        return marketplaceOrder;
    }

    @Transactional
    public Bill convertToBill(Long restaurantId, Long marketplaceOrderId) {
        MarketplaceOrder marketplaceOrder = marketplaceOrderRepository.findById(marketplaceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace order not found"));

        if (!marketplaceOrder.getRestaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Order does not belong to this restaurant");
        }

        if (marketplaceOrder.getBillId() != null) {
            return billRepository.findById(marketplaceOrder.getBillId()).orElse(null);
        }

        Bill bill = new Bill();
        bill.setRestaurantId(restaurantId);
        bill.setDailyOrderId(System.currentTimeMillis());
        bill.setLifetimeOrderId(System.currentTimeMillis());
        bill.setOrderType("online");
        bill.setCustomerName(marketplaceOrder.getCustomerName());
        bill.setCustomerWhatsapp(marketplaceOrder.getCustomerPhone());
        bill.setSubtotal(marketplaceOrder.getSubtotal() != null ? marketplaceOrder.getSubtotal() : BigDecimal.ZERO);
        bill.setTotalAmount(marketplaceOrder.getTotalAmount());
        bill.setPaymentMode("swiggy");
        bill.setPaymentStatus("pending");
        bill.setOrderStatus("draft");
        bill.setLastResetDate(java.time.LocalDate.now().toString());
        bill.setCreatedAt(System.currentTimeMillis());
        bill.setUpdatedAt(System.currentTimeMillis());

        billRepository.save(bill);
        marketplaceOrder.setBillId(bill.getId());
        marketplaceOrder.setSyncedAt(System.currentTimeMillis());
        marketplaceOrderRepository.save(marketplaceOrder);

        return bill;
    }

    private MarketplaceOrder createMarketplaceOrder(Long restaurantId, JsonNode orderData, String platformOrderId) {
        MarketplaceOrder order = new MarketplaceOrder();
        order.setRestaurantId(restaurantId);
        order.setPlatform("SWIGGY");
        order.setPlatformOrderId(platformOrderId);
        order.setPlatformStatus(extractPlatformStatus(orderData));
        order.setCustomerName(extractCustomerName(orderData));
        order.setCustomerPhone(extractCustomerPhone(orderData));
        order.setCustomerAddress(extractCustomerAddress(orderData));
        order.setSubtotal(extractSubtotal(orderData));
        order.setTaxAmount(extractTaxAmount(orderData));
        order.setTotalAmount(extractTotalAmount(orderData));
        order.setPaymentMode(extractPaymentMode(orderData));
        order.setRawPayload(orderData.toString());
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        return order;
    }

    private void updateMarketplaceOrder(MarketplaceOrder order, JsonNode orderData) {
        order.setPlatformStatus(extractPlatformStatus(orderData));
        order.setRawPayload(orderData.toString());
        order.setUpdatedAt(System.currentTimeMillis());
    }

    private List<MarketplaceOrderItem> createOrderItems(Long marketplaceOrderId, JsonNode orderData) {
        List<MarketplaceOrderItem> items = new ArrayList<>();
        JsonNode itemsNode = orderData.path("items");

        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                MarketplaceOrderItem item = new MarketplaceOrderItem();
                item.setMarketplaceOrderId(marketplaceOrderId);
                item.setPlatformItemId(itemNode.path("id").asText(""));
                item.setItemName(itemNode.path("name").asText("Unknown Item"));
                item.setVariantName(itemNode.path("variant").asText(""));
                item.setPrice(new BigDecimal(itemNode.path("price").asText("0")));
                item.setQuantity(itemNode.path("quantity").asInt(1));
                item.setItemTotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                item.setSpecialInstruction(itemNode.path("instructions").asText(""));
                items.add(item);
            }
        }
        return items;
    }

    private JsonNode parsePayload(String payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private String extractPlatformOrderId(JsonNode orderData) {
        return orderData.path("order_id").asText(orderData.path("id").asText(""));
    }

    private String extractPlatformStatus(JsonNode orderData) {
        return orderData.path("status").asText("NEW");
    }

    private String extractCustomerName(JsonNode orderData) {
        return orderData.path("customer").path("name").asText("Swiggy Customer");
    }

    private String extractCustomerPhone(JsonNode orderData) {
        return orderData.path("customer").path("phone").asText("");
    }

    private String extractCustomerAddress(JsonNode orderData) {
        return orderData.path("delivery_address").asText("");
    }

    private BigDecimal extractSubtotal(JsonNode orderData) {
        return new BigDecimal(orderData.path("subtotal").asText("0"));
    }

    private BigDecimal extractTaxAmount(JsonNode orderData) {
        return new BigDecimal(orderData.path("tax").asText("0"));
    }

    private BigDecimal extractTotalAmount(JsonNode orderData) {
        return new BigDecimal(orderData.path("total").asText("0"));
    }

    private String extractPaymentMode(JsonNode orderData) {
        return orderData.path("payment_mode").asText("online");
    }

    public JsonNode fetchOrderDetails(String apiKey, String orderId) {
        return apiClient.getOrderDetails(apiKey, orderId);
    }

    public String toString(JsonNode node) {
        return node != null ? node.toString() : "{}";
    }
}
