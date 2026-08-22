package com.khanabook.saas.controller;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.service.DbRateLimiter;
import com.khanabook.saas.service.PushNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer-facing QR ordering (Phase 2 core). Unauthenticated menu browsing and
 * order creation for a single restaurant. Prices are ALWAYS resolved server-side
 * from the menu tables — client-sent amounts are ignored. Orders land on the POS
 * as draft bills via the push notification channel.
 */
@RestController
@RequestMapping("/public/restaurants/{restaurantId}")
public class PublicOrderController {

    private static final Logger log = LoggerFactory.getLogger(PublicOrderController.class);
    private static final String QR_DEVICE_ID = "QR_ORDER";
    private static final int MAX_ITEMS_PER_ORDER = 50;

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final PushNotificationService pushNotificationService;
    private final com.khanabook.saas.service.EasebuzzPaymentService easebuzzPaymentService;
    private final DbRateLimiter qrOrderRateLimiter;

    public PublicOrderController(CategoryRepository categoryRepository,
                                 MenuItemRepository menuItemRepository,
                                 ItemVariantRepository itemVariantRepository,
                                 BillRepository billRepository,
                                 BillItemRepository billItemRepository,
                                 PushNotificationService pushNotificationService,
                                 com.khanabook.saas.service.EasebuzzPaymentService easebuzzPaymentService,
                                 @org.springframework.beans.factory.annotation.Qualifier("qrOrderRateLimiterDb")
                                 DbRateLimiter qrOrderRateLimiter) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.itemVariantRepository = itemVariantRepository;
        this.billRepository = billRepository;
        this.billItemRepository = billItemRepository;
        this.pushNotificationService = pushNotificationService;
        this.easebuzzPaymentService = easebuzzPaymentService;
        this.qrOrderRateLimiter = qrOrderRateLimiter;
    }

    // ── Menu ──────────────────────────────────────────────────────────────

    /** Customer-safe menu: only available items, only customer-safe fields. */
    @GetMapping("/menu")
    public ResponseEntity<?> menu(@PathVariable Long restaurantId) {
        List<Map<String, Object>> categories = new ArrayList<>();
        categoryRepository.findByRestaurantIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(restaurantId)
                .forEach(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("id", c.getId());
                    cm.put("name", c.getName());
                    cm.put("isVeg", c.getIsVeg());
                    cm.put("sortOrder", c.getSortOrder());
                    categories.add(cm);
                });

        List<Map<String, Object>> items = new ArrayList<>();
        for (MenuItem item : menuItemRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId)) {
            if (!Boolean.TRUE.equals(item.getIsAvailable())) continue;
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", item.getId());
            im.put("categoryId", item.getCategoryId());
            im.put("name", item.getName());
            im.put("description", item.getDescription());
            im.put("foodType", item.getFoodType());
            im.put("basePrice", item.getBasePrice());

            List<Map<String, Object>> variants = new ArrayList<>();
            if (itemVariantRepository.countByMenuItemIdAndIsDeletedFalse(item.getId()) > 0) {
                for (ItemVariant v : itemVariantRepository.findByServerMenuItemIdAndIsDeletedFalse(item.getId())) {
                    if (!Boolean.TRUE.equals(v.getIsAvailable())) continue;
                    Map<String, Object> vm = new LinkedHashMap<>();
                    vm.put("id", v.getId());
                    vm.put("name", v.getVariantName());
                    vm.put("price", v.getPrice());
                    variants.add(vm);
                }
            }
            im.put("variants", variants);
            items.add(im);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("items", items);
        return ResponseEntity.ok(result);
    }

    // ── Order ─────────────────────────────────────────────────────────────

    public record OrderItem(Long menuItemId, Long variantId, Integer quantity) {}

    public record CreateOrderRequest(List<OrderItem> items, String orderType,
                                     String tableLabel, String customerNote) {}

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@PathVariable Long restaurantId,
                                         @RequestBody CreateOrderRequest request,
                                         HttpServletRequest httpRequest) {
        if (!qrOrderRateLimiter.tryConsume(clientIp(httpRequest))) {
            return ResponseEntity.status(429).body(Map.of("error", "TOO_MANY_ORDERS"));
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items are required"));
        }
        if (request.items().size() > MAX_ITEMS_PER_ORDER) {
            return ResponseEntity.badRequest().body(Map.of("error", "too many line items"));
        }

        // Resolve every line server-side: price and availability come from the DB.
        List<BillItem> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int seq = 0;
        for (OrderItem line : request.items()) {
            int qty = line.quantity() != null ? line.quantity() : 1;
            if (qty <= 0 || qty > 20) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid quantity"));
            }
            MenuItem item = menuItemRepository.findById(line.menuItemId()).orElse(null);
            if (item == null || !item.getRestaurantId().equals(restaurantId)
                    || !Boolean.TRUE.equals(item.getIsAvailable())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "item unavailable: " + line.menuItemId()));
            }
            BigDecimal price = item.getBasePrice();
            String name = item.getName();
            if (line.variantId() != null) {
                ItemVariant variant = itemVariantRepository.findById(line.variantId()).orElse(null);
                if (variant == null || !Boolean.TRUE.equals(variant.getIsAvailable())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "variant unavailable: " + line.variantId()));
                }
                price = variant.getPrice();
                name = name + " (" + variant.getVariantName() + ")";
            }
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(lineTotal);

            BillItem bi = new BillItem();
            bi.setItemName(name);
            bi.setPrice(price);
            bi.setQuantity(qty);
            bi.setItemTotal(lineTotal);
            bi.setMenuItemId(item.getId());
            bi.setLocalId((long) ++seq);
            lines.add(bi);
        }
        if (lines.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no valid items"));
        }

        long now = System.currentTimeMillis();
        Bill bill = new Bill();
        bill.setRestaurantId(restaurantId);
        bill.setDeviceId(QR_DEVICE_ID);
        // Unique per (restaurant, device): epoch-based localId avoids collisions.
        bill.setLocalId(now % 1_000_000_000L * 1000 + (now % 1000));
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        bill.setOrderStatus("draft");
        bill.setPaymentStatus("pending");
        bill.setPaymentMode("pending");
        bill.setOrderType(normalizeOrderType(request.orderType()));
        bill.setSourceChannel("own_website");
        bill.setTotalAmount(total);
        bill.setSubtotal(total);

        // Daily order number: unique per (restaurant, lastResetDate) — allocate next.
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        Long maxDaily = billRepository.findMaxDailyOrderIdForDate(restaurantId, today);
        long dailyOrderId = (maxDaily != null ? maxDaily : 0L) + 1;
        bill.setLastResetDate(today);
        bill.setDailyOrderId(dailyOrderId);
        bill.setDailyOrderDisplay("QR" + dailyOrderId);
        bill.setLifetimeOrderId(now);

        Bill saved = billRepository.save(bill);
        for (BillItem bi : lines) {
            bi.setBillId(saved.getId());
            bi.setServerBillId(saved.getId());
            bi.setRestaurantId(restaurantId);
            bi.setDeviceId(QR_DEVICE_ID);
            bi.setCreatedAt(now);
            bi.setUpdatedAt(now);
            bi.setServerUpdatedAt(now);
            billItemRepository.save(bi);
        }

        try {
            pushNotificationService.pushToRestaurant(
                    restaurantId,
                    "New QR Order",
                    "Table/Token " + (request.tableLabel() != null ? request.tableLabel() : "-")
                            + " ordered " + lines.size() + " item(s). Total ₹" + total.toPlainString(),
                    "marketplace_order",
                    String.valueOf(saved.getId()),
                    "bill",
                    total);
        } catch (Exception e) {
            log.warn("Failed to push QR order notification: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", saved.getId());
        result.put("status", saved.getOrderStatus());
        result.put("total", total);
        result.put("paymentStatus", saved.getPaymentStatus());
        return ResponseEntity.ok(result);
    }

    /**
     * Customer self-pay: creates an Easebuzz Easy Collect link for a QR order.
     * Tenant safety: the bill must belong to the restaurant in the URL path;
     * paid bills are rejected by the payment service itself (ALREADY_PAID).
     */
    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<?> payOrder(@PathVariable Long restaurantId,
                                      @PathVariable Long orderId,
                                      HttpServletRequest httpRequest) {
        if (!qrOrderRateLimiter.tryConsume(clientIp(httpRequest))) {
            return ResponseEntity.status(429).body(Map.of("error", "TOO_MANY_REQUESTS"));
        }
        boolean belongs = billRepository.findById(orderId)
                .filter(b -> b.getRestaurantId().equals(restaurantId))
                .isPresent();
        if (!belongs) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(easebuzzPaymentService.createPaymentLinkForBill(orderId, restaurantId));
    }

    private static String normalizeOrderType(String orderType) {
        if (orderType == null) return "takeaway";
        return switch (orderType.toLowerCase()) {
            case "dine_in", "dine-in" -> "dine_in";
            case "delivery" -> "delivery";
            case "parcel" -> "parcel";
            default -> "takeaway";
        };
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}
