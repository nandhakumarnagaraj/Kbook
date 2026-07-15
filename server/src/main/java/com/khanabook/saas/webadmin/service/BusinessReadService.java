package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.webadmin.dto.BusinessDashboardResponse;
import com.khanabook.saas.webadmin.dto.BusinessMarketplaceSetupResponse;
import com.khanabook.saas.webadmin.dto.BusinessMenuListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessOrderListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessStaffListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessReadService {

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BillRepository billRepository;

    @Transactional(readOnly = true)
    public BusinessDashboardResponse getDashboard(Long restaurantId) {
        var profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        List<User> staff = getBusinessUsers(restaurantId);
        List<MenuItem> menuItems = getBusinessMenuItems(restaurantId);
        List<Bill> bills = getBusinessBills(restaurantId);

        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zoneId);
        long startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli();

        BigDecimal billRevenue = bills.stream()
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal todayBillRevenue = bills.stream()
                .filter(bill -> bill.getCreatedAt() != null && bill.getCreatedAt() >= startOfToday)
                .filter(bill -> isRevenueBillStatus(bill.getOrderStatus(), bill.getPaymentStatus()))
                .map(bill -> safeAmount(bill.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long refundedOrders = bills.stream()
                .filter(bill -> isRefundedBill(bill))
                .count();
        BigDecimal refundedAmount = bills.stream()
                .filter(bill -> isRefundedBill(bill))
                .map(bill -> safeAmount(bill.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessOrderListItemResponse> recentOrders = buildOrders(bills).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(8)
                .toList();

        return BusinessDashboardResponse.builder()
                .restaurantId(restaurantId)
                .shopName(profile.getShopName())
                .websiteEnabled(Boolean.TRUE.equals(profile.getOwnWebsiteEnabled()))
                .printerEnabled(Boolean.TRUE.equals(profile.getPrinterEnabled()))
                .kitchenPrinterEnabled(Boolean.TRUE.equals(profile.getKitchenPrinterEnabled()))
                .totalStaff(staff.size())
                .totalMenuItems(menuItems.size())
                .posOrderCount(bills.size())
                .pendingPosPayments(bills.stream()
                        .filter(this::isPendingPosPayment)
                        .count())
                .totalRevenue(billRevenue)
                .todayRevenue(todayBillRevenue)
                .refundedOrders(refundedOrders)
                .refundedAmount(refundedAmount)
                .recentOrders(recentOrders)
                .build();
    }

    @Transactional(readOnly = true)
    public BusinessMarketplaceSetupResponse getMarketplaceSetup(Long restaurantId) {
        var profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        return BusinessMarketplaceSetupResponse.builder()
                .restaurantId(restaurantId)
                .shopName(profile.getShopName())
                .paymentManagedByAdmin(true)
                .subMerchantStatus(null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BusinessOrderListItemResponse> getOrders(Long restaurantId) {
        List<Bill> bills = getBusinessBills(restaurantId);
        return buildOrders(bills).stream()
                .sorted(Comparator.comparing(BusinessOrderListItemResponse::createdAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessMenuListItemResponse> getMenu(Long restaurantId) {
        List<MenuItem> menuItems = getBusinessMenuItems(restaurantId);
        Map<Long, String> categoryNames = categoryRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(category -> !Boolean.TRUE.equals(category.getIsDeleted()))
                .collect(Collectors.toMap(Category::getId, Category::getName, (left, right) -> left));

        Map<Long, Long> variantCountByMenuId = itemVariantRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(variant -> !Boolean.TRUE.equals(variant.getIsDeleted()))
                .collect(Collectors.groupingBy(variant -> variant.getMenuItemId(), Collectors.counting()));

        return menuItems.stream()
                .map(item -> BusinessMenuListItemResponse.builder()
                        .menuItemId(item.getId())
                        .categoryName(categoryNames.get(item.getCategoryId()))
                        .name(item.getName())
                        .description(item.getDescription())
                        .foodType(item.getFoodType())
                        .basePrice(item.getBasePrice())
                        .available(Boolean.TRUE.equals(item.getIsAvailable()))
                        .stockStatus(item.getStockStatus().name())
                        .variantCount(variantCountByMenuId.getOrDefault(item.getId(), 0L))
                        .updatedAt(item.getUpdatedAt())
                        .build())
                .sorted(Comparator.comparing(BusinessMenuListItemResponse::updatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessStaffListItemResponse> getStaff(Long restaurantId) {
        return getBusinessUsers(restaurantId).stream()
                .map(user -> BusinessStaffListItemResponse.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .loginId(user.getLoginId())
                        .email(user.getEmail())
                        .whatsappNumber(user.getWhatsappNumber())
                        .role(user.getRole().name())
                        .active(Boolean.TRUE.equals(user.getIsActive()))
                        .updatedAt(user.getUpdatedAt())
                        .build())
                .sorted(Comparator.comparing(BusinessStaffListItemResponse::updatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessOrderListItemResponse getPosOrder(Long restaurantId, Long billId) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return toBillOrderResponse(bill);
    }

    @Transactional
    public void markManualRefund(Long restaurantId, Long billId, BigDecimal refundAmount, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        long now = System.currentTimeMillis();
        bill.setRefundAmount(refundAmount);
        bill.setCancelReason(reason);
        bill.setOrderStatus("cancelled");
        bill.setPaymentStatus("refunded");
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);
    }

    private List<BusinessOrderListItemResponse> buildOrders(List<Bill> bills) {
        return bills.stream()
                .map(bill -> toBillOrderResponse(bill))
                .toList();
    }

    private List<User> getBusinessUsers(Long restaurantId) {
        return userRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
    }

    private List<MenuItem> getBusinessMenuItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .toList();
    }

    private List<Bill> getBusinessBills(Long restaurantId) {
        return billRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(restaurantId, 0L).stream()
                .filter(bill -> !Boolean.TRUE.equals(bill.getIsDeleted()))
                .toList();
    }

    private boolean isRevenueBillStatus(String orderStatus, String paymentStatus) {
        return ("completed".equalsIgnoreCase(orderStatus) || "paid".equalsIgnoreCase(orderStatus))
                && ("success".equalsIgnoreCase(paymentStatus) || "paid".equalsIgnoreCase(paymentStatus));
    }

    private boolean isPendingPosPayment(Bill bill) {
        return "draft".equalsIgnoreCase(bill.getOrderStatus())
                && "pending".equalsIgnoreCase(bill.getPaymentStatus());
    }

    private boolean isRefundedBill(Bill bill) {
        return bill.getRefundAmount() != null
                && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                && "refunded".equalsIgnoreCase(bill.getPaymentStatus());
    }

    private BusinessOrderListItemResponse toBillOrderResponse(Bill bill) {
        return BusinessOrderListItemResponse.builder()
                .sourceType(bill.getSourceChannel() != null && !bill.getSourceChannel().isBlank()
                        ? bill.getSourceChannel() : "POS")
                .orderId(bill.getId())
                .orderCode(bill.getDailyOrderDisplay() != null && !bill.getDailyOrderDisplay().isBlank()
                        ? bill.getDailyOrderDisplay()
                        : bill.getLifetimeOrderId() != null && bill.getLifetimeOrderId() > 0
                                ? "INV" + bill.getLifetimeOrderId()
                                : bill.getInvoiceNumber() != null && !bill.getInvoiceNumber().isBlank()
                                        ? bill.getInvoiceNumber()
                                        : "ORD-" + bill.getId())
                .customerName(bill.getCustomerName())
                .customerContact(bill.getCustomerWhatsapp())
                .orderStatus(normalizeLabel(bill.getOrderStatus()))
                .paymentStatus(normalizeLabel(bill.getPaymentStatus()))
                .paymentMethod(normalizeLabel(bill.getPaymentMode()))
                .totalAmount(safeAmount(bill.getTotalAmount()))
                .gatewayPaidAmount(null)
                .refundAmount(bill.getRefundAmount())
                .refundStatus(bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                        ? "Refunded" : "Not refunded")
                .refundMode(null)
                .cancelReason(bill.getCancelReason())
                .manualRefundAllowed(canManualRefund(bill))
                .gatewayRefundAllowed(false)
                .createdAt(bill.getCreatedAt())
                .build();
    }

    private boolean canManualRefund(Bill bill) {
        if (!isRefundableOrderStatus(bill.getOrderStatus()) || !"success".equalsIgnoreCase(bill.getPaymentStatus())) {
            return false;
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        return true;
    }

    private boolean isRefundableOrderStatus(String orderStatus) {
        return "completed".equalsIgnoreCase(orderStatus) || "cancelled".equalsIgnoreCase(orderStatus);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim()
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
