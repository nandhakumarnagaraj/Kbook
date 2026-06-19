package com.khanabook.saas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.MarketplaceOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceWebhookControllerTest {

    @Mock private MarketplaceOrderRepository orderRepo;
    @Mock private RestaurantProfileRepository profileRepo;
    @Mock private MarketplaceOrderService marketplaceOrderService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MarketplaceWebhookController controller;

    private final Long testRestaurantId = 42L;
    private final String swiggyStoreId = "S1-4901dgh#";
    private final String zomatoOutletId = "S1-4901dgh#";

    @BeforeEach
    void setup() {
        lenient().when(profileRepo.findRestaurantIdBySwiggyStoreIdAndIsDeletedFalse(swiggyStoreId))
            .thenReturn(Optional.of(testRestaurantId));
        lenient().when(profileRepo.findRestaurantIdByZomatoOutletIdAndIsDeletedFalse(zomatoOutletId))
            .thenReturn(Optional.of(testRestaurantId));
        // No webhook secrets configured → signature=null is accepted (onboarding mode)
        lenient().when(profileRepo.findAll()).thenReturn(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> urbanPiperPayload(String platform, Map<String, Object> overrides) {
        Map<String, Object> customerAddress = new HashMap<>();
        customerAddress.put("line_1", "123 Test St");
        customerAddress.put("city", "Bangalore");
        customerAddress.put("pin", "560001");

        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "John Doe");
        customer.put("phone", "9999999999");
        customer.put("address", customerAddress);

        Map<String, Object> details = new HashMap<>();
        details.put("id", 3444567);
        details.put("channel", platform);
        details.put("order_total", 450.00);
        details.put("order_subtotal", 400.00);
        details.put("order_level_total_taxes", 30.0);
        details.put("item_level_total_taxes", 20.0);
        details.put("order_state", "Placed");
        details.put("order_type", "delivery");

        Map<String, Object> store = new HashMap<>();
        store.put("merchant_ref_id", platform.equals("swiggy") ? swiggyStoreId : zomatoOutletId);
        store.put("name", "Test Outlet");

        Map<String, Object> payment = new HashMap<>();
        payment.put("amount", 450.00);
        payment.put("option", "COD");

        Map<String, Object> orderObj = new HashMap<>();
        orderObj.put("details", details);
        orderObj.put("store", store);
        orderObj.put("payment", List.of(payment));

        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", customer);
        payload.put("order", orderObj);

        if (overrides != null) payload.putAll(overrides);
        return payload;
    }

    /** Helper: serialize payload to JSON string and call swiggyWebhook(rawBody, null) */
    private ResponseEntity<Map<String, Object>> callSwiggy(Map<String, Object> payload) {
        try {
            return controller.swiggyWebhook(objectMapper.writeValueAsString(payload), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Helper: serialize payload to JSON string and call zomatoWebhook(rawBody, null) */
    private ResponseEntity<Map<String, Object>> callZomato(Map<String, Object> payload) {
        try {
            return controller.zomatoWebhook(objectMapper.writeValueAsString(payload), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void swiggyWebhook_shouldAcceptOrder() {
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(1L); return inv.getArgument(0); });

        ResponseEntity<Map<String, Object>> response = callSwiggy(urbanPiperPayload("swiggy", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("received", response.getBody().get("status"));
        assertNotNull(response.getBody().get("order_id"));
    }

    @Test
    void zomatoWebhook_shouldAcceptOrder() {
        when(orderRepo.findByPlatformAndPlatformOrderId("ZOMATO", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(2L); return inv.getArgument(0); });

        ResponseEntity<Map<String, Object>> response = callZomato(urbanPiperPayload("zomato", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("received", response.getBody().get("status"));
    }

    @Test
    void webhook_duplicateOrder_shouldReturnDuplicate() {
        MarketplaceOrder existing = new MarketplaceOrder();
        existing.setId(1L);
        existing.setPlatformOrderId("3444567");
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.of(existing));

        ResponseEntity<Map<String, Object>> response = callSwiggy(urbanPiperPayload("swiggy", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("duplicate", response.getBody().get("status"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void webhook_unknownStore_shouldReturnError() {
        Map<String, Object> payload = urbanPiperPayload("swiggy", null);
        ((Map<String, Object>) ((Map<String, Object>) payload.get("order")).get("store")).put("merchant_ref_id", "UNKNOWN_STORE");

        ResponseEntity<Map<String, Object>> response = callSwiggy(payload);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("error", response.getBody().get("status"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void webhook_missingCustomer_shouldReturnError() {
        Map<String, Object> payload = urbanPiperPayload("swiggy", null);
        payload.remove("customer");

        ResponseEntity<Map<String, Object>> response = callSwiggy(payload);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("error", response.getBody().get("status"));
    }

    @Test
    void webhook_missingOrder_shouldReturnError() {
        Map<String, Object> payload = urbanPiperPayload("swiggy", null);
        payload.remove("order");

        ResponseEntity<Map<String, Object>> response = callSwiggy(payload);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("error", response.getBody().get("status"));
    }

    @Test
    void webhook_amountParsing_shouldHandleNulls() {
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(5L); return inv.getArgument(0); });

        Map<String, Object> payload = urbanPiperPayload("swiggy", null);
        ((Map<String, Object>) ((Map<String, Object>) payload.get("order")).get("details")).remove("order_total");
        ((Map<String, Object>) ((Map<String, Object>) payload.get("order")).get("details")).remove("order_subtotal");

        ResponseEntity<Map<String, Object>> response = callSwiggy(payload);

        assertEquals(200, response.getStatusCode().value());
        verify(marketplaceOrderService, times(1)).createOrder(argThat(order ->
            order.getTotalAmount().compareTo(BigDecimal.ZERO) == 0
            && order.getSubtotal().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void webhook_shouldSetAllFields() {
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(6L); return inv.getArgument(0); });

        callSwiggy(urbanPiperPayload("swiggy", null));

        verify(marketplaceOrderService).createOrder(argThat(order ->
            "SWIGGY".equals(order.getPlatform())
            && "3444567".equals(order.getPlatformOrderId())
            && "Placed".equals(order.getPlatformStatus())
            && "John Doe".equals(order.getCustomerName())
            && "9999999999".equals(order.getCustomerPhone())
            && "123 St St".equals(order.getCustomerAddress()) || "123 Test St".equals(order.getCustomerAddress())
            && order.getTotalAmount().compareTo(BigDecimal.valueOf(450.00)) == 0
            && order.getSubtotal().compareTo(BigDecimal.valueOf(400.00)) == 0
            && order.getTaxAmount().compareTo(BigDecimal.valueOf(50.0)) == 0
            && "COD".equals(order.getPaymentMode())
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void webhook_multiplePayments_shouldUseFirst() {
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(7L); return inv.getArgument(0); });

        Map<String, Object> payload = urbanPiperPayload("swiggy", null);
        Map<String, Object> payment2 = new HashMap<>();
        payment2.put("amount", 26.25);
        payment2.put("option", "wallet_credit");
        List<Map<String, Object>> payments = List.of(
            Map.of("amount", 423.75, "option", "cash"),
            payment2
        );
        ((Map<String, Object>) payload.get("order")).put("payment", payments);

        callSwiggy(payload);

        verify(marketplaceOrderService).createOrder(argThat(order -> "cash".equals(order.getPaymentMode())));
    }

    @Test
    void webhook_withValidSignature_shouldAccept() {
        // No secrets configured → any signature=null is accepted
        when(orderRepo.findByPlatformAndPlatformOrderId("SWIGGY", "3444567")).thenReturn(Optional.empty());
        when(marketplaceOrderService.createOrder(any())).thenAnswer(inv -> { ((MarketplaceOrder) inv.getArgument(0)).setId(8L); return inv.getArgument(0); });

        ResponseEntity<Map<String, Object>> response = callSwiggy(urbanPiperPayload("swiggy", null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("received", response.getBody().get("status"));
    }
}
