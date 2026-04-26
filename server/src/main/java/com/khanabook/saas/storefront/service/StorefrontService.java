package com.khanabook.saas.storefront.service;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.storefront.dto.CreateCustomerOrderRequest;
import com.khanabook.saas.storefront.dto.CreateCustomerOrderResponse;
import com.khanabook.saas.storefront.dto.CustomerOrderStatusResponse;
import com.khanabook.saas.storefront.dto.MerchantCustomerOrderDetailResponse;
import com.khanabook.saas.storefront.dto.MerchantCustomerOrderSummaryResponse;
import com.khanabook.saas.storefront.dto.StorefrontCatalogResponse;
import com.khanabook.saas.storefront.dto.UpdateCustomerOrderStatusRequest;
import com.khanabook.saas.storefront.entity.CustomerOrder;
import com.khanabook.saas.storefront.entity.CustomerOrderItem;
import com.khanabook.saas.storefront.repository.CustomerOrderItemRepository;
import com.khanabook.saas.storefront.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorefrontService {

    private static final Set<String> ALLOWED_FULFILLMENT_TYPES = Set.of("PICKUP", "DELIVERY", "DINE_IN");
    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.of("COD", "UPI", "ONLINE", "POS");
    private static final Set<String> ALLOWED_MERCHANT_ORDER_STATUSES = Set.of(
            "PENDING_CONFIRMATION", "ACCEPTED", "REJECTED", "PREPARING", "READY", "COMPLETED", "CANCELLED"
    );
    private static final Map<String, Set<String>> ORDER_STATUS_TRANSITIONS = Map.of(
            "PENDING_CONFIRMATION", Set.of("ACCEPTED", "REJECTED", "CANCELLED"),
            "ACCEPTED", Set.of("PREPARING", "READY", "CANCELLED"),
            "PREPARING", Set.of("READY", "CANCELLED"),
            "READY", Set.of("COMPLETED", "CANCELLED"),
            "REJECTED", Set.of(),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of()
    );

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerOrderItemRepository customerOrderItemRepository;

    @Transactional(readOnly = true)
    public StorefrontCatalogResponse getCatalog(Long restaurantId) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found"));

        if (!Boolean.TRUE.equals(profile.getOwnWebsiteEnabled())) {
            throw new IllegalArgumentException("This store is not accepting public online orders");
        }

        List<Category> categories = categoryRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(
                restaurantId, 0L
        ).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getSortOrder()).orElse(0)))
                .toList();

        List<MenuItem> menuItems = menuItemRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(
                restaurantId, 0L
        ).stream()
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .toList();

        List<ItemVariant> variants = itemVariantRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(
                restaurantId, 0L
        ).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getIsDeleted()))
                .toList();

        Map<Long, List<ItemVariant>> variantsByMenuItem = variants.stream()
                .collect(Collectors.groupingBy(ItemVariant::getMenuItemId));

        Map<Long, List<MenuItem>> menuItemsByCategory = menuItems.stream()
                .filter(this::isPubliclyOrderable)
                .sorted(Comparator.comparing(MenuItem::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.groupingBy(MenuItem::getCategoryId));

        List<StorefrontCatalogResponse.CategoryView> categoryViews = categories.stream()
                .map(category -> StorefrontCatalogResponse.CategoryView.builder()
                        .categoryId(category.getId())
                        .name(category.getName())
                        .isVeg(category.getIsVeg())
                        .items(menuItemsByCategory.getOrDefault(category.getId(), List.of()).stream()
                                .map(item -> StorefrontCatalogResponse.MenuItemView.builder()
                                        .menuItemId(item.getId())
                                        .name(item.getName())
                                        .description(item.getDescription())
                                        .foodType(item.getFoodType())
                                        .basePrice(item.getBasePrice())
                                        .available(Boolean.TRUE.equals(item.getIsAvailable()))
                                        .stockStatus(item.getStockStatus().name())
                                        .variants(variantsByMenuItem.getOrDefault(item.getId(), List.of()).stream()
                                                .filter(this::isPubliclyOrderable)
                                                .sorted(Comparator.comparing(v -> Optional.ofNullable(v.getSortOrder()).orElse(0)))
                                                .map(variant -> StorefrontCatalogResponse.VariantView.builder()
                                                        .variantId(variant.getId())
                                                        .name(variant.getVariantName())
                                                        .price(variant.getPrice())
                                                        .available(Boolean.TRUE.equals(variant.getIsAvailable()))
                                                        .stockStatus(variant.getStockStatus().name())
                                                        .build())
                                                .toList())
                                        .build())
                                .toList())
                        .build())
                .filter(category -> !category.getItems().isEmpty())
                .toList();

        return StorefrontCatalogResponse.builder()
                .restaurantId(restaurantId)
                .shopName(profile.getShopName())
                .currency(Optional.ofNullable(profile.getCurrency()).filter(s -> !s.isBlank()).orElse("INR"))
                .whatsappNumber(profile.getWhatsappNumber())
                .reviewUrl(profile.getReviewUrl())
                .categories(categoryViews)
                .build();
    }

    @Transactional
    public CreateCustomerOrderResponse createCustomerOrder(Long restaurantId, CreateCustomerOrderRequest request) {
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found"));

        if (!Boolean.TRUE.equals(profile.getOwnWebsiteEnabled())) {
            throw new IllegalArgumentException("This store is not accepting public online orders");
        }

        Map<Long, MenuItem> menuItems = menuItemRepository.findAllById(
                request.getItems().stream().map(CreateCustomerOrderRequest.LineItem::getMenuItemId).collect(Collectors.toSet())
        ).stream()
                .filter(item -> restaurantId.equals(item.getRestaurantId()))
                .collect(Collectors.toMap(MenuItem::getId, item -> item));

        Map<Long, ItemVariant> variants = itemVariantRepository.findAllById(
                request.getItems().stream()
                        .map(CreateCustomerOrderRequest.LineItem::getVariantId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        ).stream()
                .filter(variant -> restaurantId.equals(variant.getRestaurantId()))
                .collect(Collectors.toMap(ItemVariant::getId, variant -> variant));

        long now = System.currentTimeMillis();
        List<CustomerOrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateCustomerOrderRequest.LineItem lineItem : request.getItems()) {
            MenuItem menuItem = menuItems.get(lineItem.getMenuItemId());
            if (menuItem == null || !isPubliclyOrderable(menuItem)) {
                throw new IllegalArgumentException("Selected menu item is not available");
            }

            ItemVariant variant = null;
            BigDecimal unitPrice = menuItem.getBasePrice();
            String variantName = null;
            if (lineItem.getVariantId() != null) {
                variant = variants.get(lineItem.getVariantId());
                if (variant == null || !Objects.equals(variant.getMenuItemId(), menuItem.getId()) || !isPubliclyOrderable(variant)) {
                    throw new IllegalArgumentException("Selected variant is not available");
                }
                unitPrice = variant.getPrice();
                variantName = variant.getVariantName();
            }

            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(lineItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            CustomerOrderItem orderItem = new CustomerOrderItem();
            orderItem.setMenuItemId(menuItem.getId());
            orderItem.setItemVariantId(variant != null ? variant.getId() : null);
            orderItem.setItemName(menuItem.getName());
            orderItem.setVariantName(variantName);
            orderItem.setQuantity(lineItem.getQuantity());
            orderItem.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
            orderItem.setLineTotal(lineTotal);
            orderItem.setSpecialInstruction(blankToNull(lineItem.getSpecialInstruction()));
            orderItem.setCreatedAt(now);
            orderItems.add(orderItem);
        }

        CustomerOrder order = new CustomerOrder();
        order.setRestaurantId(restaurantId);
        order.setPublicOrderCode(generatePublicOrderCode(restaurantId));
        order.setTrackingToken(UUID.randomUUID().toString().replace("-", ""));
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerPhone(blankToNull(request.getCustomerPhone()));
        order.setCustomerNote(blankToNull(request.getCustomerNote()));
        order.setFulfillmentType(normalizeUpper(request.getFulfillmentType(), ALLOWED_FULFILLMENT_TYPES));
        order.setOrderStatus("PENDING_CONFIRMATION");
        order.setPaymentStatus("PENDING");
        order.setPaymentMethod(normalizeUpper(request.getPaymentMethod(), ALLOWED_PAYMENT_METHODS));
        order.setSourceChannel("B2C_WEB");
        order.setCurrency(Optional.ofNullable(profile.getCurrency()).filter(s -> !s.isBlank()).orElse("INR"));
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        customerOrderRepository.save(order);

        orderItems.forEach(item -> item.setCustomerOrderId(order.getId()));
        customerOrderItemRepository.saveAll(orderItems);

        return CreateCustomerOrderResponse.builder()
                .orderId(order.getId())
                .publicOrderCode(order.getPublicOrderCode())
                .trackingToken(order.getTrackingToken())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerOrderStatusResponse getOrderStatus(String trackingToken) {
        CustomerOrder order = customerOrderRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        List<CustomerOrderStatusResponse.LineItem> items = customerOrderItemRepository
                .findByCustomerOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(item -> CustomerOrderStatusResponse.LineItem.builder()
                        .itemName(item.getItemName())
                        .variantName(item.getVariantName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .specialInstruction(item.getSpecialInstruction())
                        .build())
                .toList();

        return CustomerOrderStatusResponse.builder()
                .publicOrderCode(order.getPublicOrderCode())
                .restaurantId(order.getRestaurantId())
                .customerName(order.getCustomerName())
                .fulfillmentType(order.getFulfillmentType())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MerchantCustomerOrderSummaryResponse> listMerchantOrders(Long restaurantId) {
        return customerOrderRepository.findByRestaurantIdOrderByCreatedAtDescIdDesc(restaurantId).stream()
                .map(this::toMerchantSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantCustomerOrderDetailResponse getMerchantOrder(Long restaurantId, Long orderId) {
        CustomerOrder order = getMerchantScopedOrder(restaurantId, orderId);
        return toMerchantDetail(order, customerOrderItemRepository.findByCustomerOrderIdOrderByIdAsc(order.getId()));
    }

    @Transactional
    public MerchantCustomerOrderDetailResponse updateMerchantOrderStatus(
            Long restaurantId,
            Long orderId,
            UpdateCustomerOrderStatusRequest request
    ) {
        CustomerOrder order = getMerchantScopedOrder(restaurantId, orderId);
        String nextStatus = normalizeUpper(request.getOrderStatus(), ALLOWED_MERCHANT_ORDER_STATUSES);
        validateStatusTransition(order.getOrderStatus(), nextStatus);

        long now = System.currentTimeMillis();
        order.setOrderStatus(nextStatus);
        if ("REJECTED".equals(nextStatus) || "CANCELLED".equals(nextStatus)) {
            order.setPaymentStatus("FAILED");
        }
        order.setCustomerNote(mergeCustomerNote(order.getCustomerNote(), request.getCustomerNote()));
        order.setUpdatedAt(now);
        customerOrderRepository.save(order);

        return toMerchantDetail(order, customerOrderItemRepository.findByCustomerOrderIdOrderByIdAsc(order.getId()));
    }

    private boolean isPubliclyOrderable(MenuItem item) {
        return Boolean.TRUE.equals(item.getIsAvailable()) &&
                item.getStockStatus() != MenuItem.StockStatus.OUT_OF_STOCK;
    }

    private boolean isPubliclyOrderable(ItemVariant variant) {
        return Boolean.TRUE.equals(variant.getIsAvailable()) &&
                variant.getStockStatus() != MenuItem.StockStatus.OUT_OF_STOCK;
    }

    private String generatePublicOrderCode(Long restaurantId) {
        String prefix = "KB" + restaurantId;
        String candidate;
        do {
            candidate = prefix + "-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT);
        } while (customerOrderRepository.existsByPublicOrderCode(candidate));
        return candidate;
    }

    private String normalizeUpper(String raw, Set<String> allowedValues) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Required field is missing");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported value: " + raw);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CustomerOrder getMerchantScopedOrder(Long restaurantId, Long orderId) {
        return customerOrderRepository.findByRestaurantIdAndId(restaurantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    private void validateStatusTransition(String currentStatus, String nextStatus) {
        if (Objects.equals(currentStatus, nextStatus)) {
            return;
        }
        Set<String> allowedTransitions = ORDER_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTransitions.contains(nextStatus)) {
            throw new IllegalArgumentException("Unsupported order status transition: " + currentStatus + " -> " + nextStatus);
        }
    }

    private String mergeCustomerNote(String existingNote, String requestedNote) {
        String trimmedRequest = blankToNull(requestedNote);
        return trimmedRequest != null ? trimmedRequest : existingNote;
    }

    private MerchantCustomerOrderSummaryResponse toMerchantSummary(CustomerOrder order) {
        return MerchantCustomerOrderSummaryResponse.builder()
                .orderId(order.getId())
                .publicOrderCode(order.getPublicOrderCode())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .fulfillmentType(order.getFulfillmentType())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .sourceChannel(order.getSourceChannel())
                .currency(order.getCurrency())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private MerchantCustomerOrderDetailResponse toMerchantDetail(CustomerOrder order, List<CustomerOrderItem> items) {
        return MerchantCustomerOrderDetailResponse.builder()
                .orderId(order.getId())
                .restaurantId(order.getRestaurantId())
                .publicOrderCode(order.getPublicOrderCode())
                .trackingToken(order.getTrackingToken())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .customerNote(order.getCustomerNote())
                .fulfillmentType(order.getFulfillmentType())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .sourceChannel(order.getSourceChannel())
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items.stream()
                        .map(item -> MerchantCustomerOrderDetailResponse.LineItem.builder()
                                .menuItemId(item.getMenuItemId())
                                .itemVariantId(item.getItemVariantId())
                                .itemName(item.getItemName())
                                .variantName(item.getVariantName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .lineTotal(item.getLineTotal())
                                .specialInstruction(item.getSpecialInstruction())
                                .build())
                        .toList())
                .build();
    }
}
